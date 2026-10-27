package dev.geckolite.runtime;

import java.io.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.*;
import java.util.zip.*;

/** Local signed-package installation. All output paths are owned by this class. */
public final class CorePackages {
  private static final long MAX_CORE=512L*1024*1024;
  private static final long MAX_PACKAGE=MAX_CORE+1024*1024;
  private static final Object INSTALL_MUTEX=new Object();
  private static final Set<String> OUTER=new HashSet<>(Arrays.asList("manifest.json","manifest.sig","core.apk"));
  private static final List<String> CONTRACT_FIELDS=Arrays.asList("schemaVersion","abi","minApi","pageAlignment","revision","profile","releaseSequence","bridgeFingerprint","coreSha256","coreSize","buildId");
  private CorePackages() { }

  public static String install(Path root, InputStream source, byte[] contractBytes,
      Map<String,PublicKey> trustedKeys) throws CoreException {
    synchronized(INSTALL_MUTEX) {
      Path stage=null;
      try {
        Files.createDirectories(root);
        try(FileChannel channel=FileChannel.open(root.resolve("install.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            FileLock ignored=channel.lock()) {
          // Only installer scratch is removed under the exclusive install lock.
          try(DirectoryStream<Path> entries=Files.newDirectoryStream(root,".stage-*")) {
            for(Path entry:entries) removeStage(entry);
          }
          stage=Files.createTempDirectory(root,".stage-");
          Path packet=stage.resolve("input.zip");
          try(OutputStream out=Files.newOutputStream(packet)){copy(source,out,MAX_PACKAGE);}
          byte[] raw, envelope;
          try(ZipFile zip=new ZipFile(packet.toFile())) {
            Set<String> names=new HashSet<>();Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()) {
              ZipEntry e=entries.nextElement();
              if(!OUTER.contains(e.getName())||!names.add(e.getName())||e.isDirectory())throw fail(CoreException.Code.INVALID_PACKAGE,"Unexpected/duplicate package entry");
            }
            if(!names.equals(OUTER))throw fail(CoreException.Code.INVALID_PACKAGE,"Incomplete package");
            raw=read(zip,"manifest.json",65536);envelope=read(zip,"manifest.sig",2048);
            Map<String,Object> manifest=authenticate(raw,envelope,contractBytes,trustedKeys);
            long size=number(manifest,"coreSize");
            Path core=stage.resolve("core.apk");
            try(InputStream in=zip.getInputStream(zip.getEntry("core.apk"));OutputStream out=Files.newOutputStream(core)) {copy(in,out,size);}
            verifyCore(core,manifest);
          }
          String id=sha(raw);
          Files.delete(packet);
          Files.write(stage.resolve("manifest.json"),raw);Files.write(stage.resolve("manifest.sig"),envelope);
          Files.createFile(stage.resolve("lease"));
          for(String name:Arrays.asList("core.apk","manifest.json","manifest.sig")) {
            Path p=stage.resolve(name);
            try(FileChannel sync=FileChannel.open(p,StandardOpenOption.WRITE)){sync.force(true);}
            if(!p.toFile().setReadable(true,true)||!p.toFile().setWritable(false,false))throw new IOException("Cannot set immutable package permissions");
          }
          Path target=root.resolve(id);
          if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)) {
            validateInstalled(root,id,contractBytes,trustedKeys);
            removeStage(stage);stage=null;return id;
          }
          Files.move(stage,target,StandardCopyOption.ATOMIC_MOVE);stage=null;
          return id;
        }
      } catch(CoreException e){throw e;}
      catch(ZipException e){throw new CoreException(CoreException.Code.INVALID_PACKAGE,"Invalid ZIP",e);}
      catch(IOException|RuntimeException e){throw new CoreException(CoreException.Code.IO_ERROR,"Local installation failed",e);}
      finally {if(stage!=null)try{removeStage(stage);}catch(IOException ignored){}}
    }
  }

  public static Path validateInstalled(Path root, String id, byte[] contract,
      Map<String,PublicKey> keys) throws CoreException {
    if(id==null||!id.matches("[a-f0-9]{64}"))throw fail(CoreException.Code.INVALID_PACKAGE,"Invalid installed package ID");
    Path dir=root.resolve(id);
    if(!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS))throw fail(CoreException.Code.CORE_NOT_INSTALLED,"CORE_NOT_INSTALLED");
    try {
      for(String name:Arrays.asList("core.apk","manifest.json","manifest.sig","lease"))
        if(!Files.isRegularFile(dir.resolve(name),LinkOption.NOFOLLOW_LINKS))throw fail(CoreException.Code.CORE_CORRUPT,"Installed file missing or linked");
      byte[] raw=boundedFile(dir.resolve("manifest.json"),65536), envelope=boundedFile(dir.resolve("manifest.sig"),2048);
      if(!sha(raw).equals(id))throw fail(CoreException.Code.CORE_CORRUPT,"Installed identity mismatch");
      Map<String,Object> manifest=authenticate(raw,envelope,contract,keys);
      Path core=dir.resolve("core.apk");verifyCore(core,manifest);return core;
    } catch(CoreException e){throw e;}
    catch(IOException e){throw new CoreException(CoreException.Code.CORE_CORRUPT,"Installed package unreadable",e);}
  }

  private static Map<String,Object> authenticate(byte[] raw, byte[] envelope, byte[] host,
      Map<String,PublicKey> keys) throws CoreException {
    Map<String,Object> signature=StrictJson.object(envelope);
    if(!signature.keySet().equals(new HashSet<>(Arrays.asList("keyId","signature"))))throw fail(CoreException.Code.INVALID_PACKAGE,"Invalid signature envelope");
    String keyId=string(signature,"keyId");
    if(!keyId.matches("[A-Za-z0-9._-]{1,32}"))throw fail(CoreException.Code.INVALID_SIGNATURE,"Invalid key ID");
    PublicKey key=keys.get(keyId);
    try {
      if(!(key instanceof ECPublicKey))throw new GeneralSecurityException("Untrusted key");
      AlgorithmParameters parameters=AlgorithmParameters.getInstance("EC");parameters.init(new ECGenParameterSpec("secp256r1"));
      ECParameterSpec p=parameters.getParameterSpec(ECParameterSpec.class),actual=((ECPublicKey)key).getParams();
      if(!p.getCurve().equals(actual.getCurve())||!p.getGenerator().equals(actual.getGenerator())||!p.getOrder().equals(actual.getOrder())||p.getCofactor()!=actual.getCofactor())throw new GeneralSecurityException("P-256 required");
      byte[] bytes=Base64.getDecoder().decode(string(signature,"signature"));
      if(bytes.length>80)throw new GeneralSecurityException("Signature too long");
      Signature verifier=Signature.getInstance("SHA256withECDSA");verifier.initVerify(key);verifier.update(raw);
      if(!verifier.verify(bytes))throw new GeneralSecurityException("Bad signature");
    } catch(GeneralSecurityException|IllegalArgumentException e){throw new CoreException(CoreException.Code.INVALID_SIGNATURE,"Package signature rejected",e);}
    // Parse signed manifest only after signature verification. The APK contract
    // independently pins the exact build; a trusted signature alone cannot pair it.
    Map<String,Object> m=StrictJson.object(raw),contract=StrictJson.object(host);
    if(!Objects.equals(contract.get("manifestSha256"),sha(raw)))throw fail(CoreException.Code.CORE_INCOMPATIBLE,"Manifest does not match this bridge");
    for(String field:CONTRACT_FIELDS)if(!Objects.equals(m.get(field),contract.get(field)))throw fail(CoreException.Code.CORE_INCOMPATIBLE,"Bridge/core contract mismatch: "+field);
    Set<String> expected=new HashSet<>(CONTRACT_FIELDS);expected.add("files");
    if(!m.keySet().equals(expected)||number(m,"schemaVersion")!=1||!string(m,"abi").equals("arm64-v8a")||number(m,"minApi")!=26||number(m,"pageAlignment")!=16384||number(m,"releaseSequence")<1||number(m,"releaseSequence")>2147483647L)
      throw fail(CoreException.Code.CORE_INCOMPATIBLE,"Unsupported core contract");
    if(number(m,"coreSize")<=0||number(m,"coreSize")>MAX_CORE||!string(m,"coreSha256").matches("[a-f0-9]{64}"))throw fail(CoreException.Code.INVALID_PACKAGE,"Invalid payload bounds");
    return m;
  }

  @SuppressWarnings("unchecked")
  private static void verifyCore(Path core, Map<String,Object> manifest) throws CoreException,IOException {
    if(Files.size(core)!=number(manifest,"coreSize")||!hashFile(core).equals(string(manifest,"coreSha256")))throw fail(CoreException.Code.CORE_CORRUPT,"Core container digest mismatch");
    Object values=manifest.get("files");if(!(values instanceof List)||((List<?>)values).size()>64)throw fail(CoreException.Code.INVALID_PACKAGE,"Invalid file list");
    Map<String,Map<String,Object>> files=new HashMap<>();long total=0;
    for(Object value:(List<?>)values) {
      if(!(value instanceof Map))throw fail(CoreException.Code.INVALID_PACKAGE,"Invalid file record");Map<String,Object> f=(Map<String,Object>)value;
      String path=string(f,"path");long size=number(f,"size");
      if(!(path.equals("assets/omni.ja")||path.matches("lib/arm64-v8a/lib[A-Za-z0-9_-]+\\.so"))||files.put(path,f)!=null||size<0||size>MAX_CORE||!string(f,"sha256").matches("[a-f0-9]{64}"))throw fail(CoreException.Code.INVALID_PACKAGE,"Invalid core path/digest/size");
      total+=size;if(total>MAX_CORE)throw fail(CoreException.Code.INVALID_PACKAGE,"Core expands beyond limit");
    }
    if(!files.containsKey("assets/omni.ja")||!files.containsKey("lib/arm64-v8a/libmozglue.so")||!files.containsKey("lib/arm64-v8a/libxul.so"))throw fail(CoreException.Code.INVALID_PACKAGE,"Core closure incomplete");
    try(ZipFile zip=new ZipFile(core.toFile())) {
      Set<String> seen=new HashSet<>();Enumeration<? extends ZipEntry> entries=zip.entries();
      while(entries.hasMoreElements()) {
        ZipEntry e=entries.nextElement();Map<String,Object> f=files.get(e.getName());
        if(f==null||!seen.add(e.getName())||e.isDirectory()||e.getSize()!=number(f,"size")||e.getMethod()!=ZipEntry.STORED)throw fail(CoreException.Code.CORE_CORRUPT,"Unexpected core entry");
        MessageDigest digest=digest();long count=0;
        try(InputStream in=zip.getInputStream(e)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){count+=n;if(count>number(f,"size"))throw fail(CoreException.Code.CORE_CORRUPT,"Core entry exceeds limit");digest.update(buffer,0,n);}}
        if(count!=number(f,"size")||!hex(digest.digest()).equals(string(f,"sha256")))throw fail(CoreException.Code.CORE_CORRUPT,"Core file digest mismatch");
      }
      if(!seen.equals(files.keySet()))throw fail(CoreException.Code.CORE_CORRUPT,"Core file missing");
    }
  }
  private static void copy(InputStream in,OutputStream out,long max) throws IOException,CoreException {
    byte[] buffer=new byte[65536];long count=0;int n;
    while((n=in.read(buffer))!=-1){count+=n;if(count>max)throw fail(CoreException.Code.INVALID_PACKAGE,"Input exceeds bounded size");out.write(buffer,0,n);}
  }
  private static byte[] read(ZipFile zip,String name,int max) throws IOException,CoreException {
    ZipEntry entry=zip.getEntry(name);if(entry==null||entry.getSize()>max)throw fail(CoreException.Code.INVALID_PACKAGE,"Metadata missing/too large");
    try(InputStream in=zip.getInputStream(entry);ByteArrayOutputStream out=new ByteArrayOutputStream()){copy(in,out,max);return out.toByteArray();}
  }
  static byte[] boundedFile(Path path,int max) throws IOException,CoreException {
    if(Files.size(path)>max)throw fail(CoreException.Code.INVALID_PACKAGE,"Metadata too large");
    try(InputStream in=Files.newInputStream(path);ByteArrayOutputStream out=new ByteArrayOutputStream()){copy(in,out,max);return out.toByteArray();}
  }
  static String string(Map<String,Object> map,String key) throws CoreException {
    Object value=map.get(key);if(!(value instanceof String))throw fail(CoreException.Code.INVALID_PACKAGE,"String field required: "+key);return (String)value;
  }
  static long number(Map<String,Object> map,String key) throws CoreException {
    Object value=map.get(key);if(!(value instanceof Long))throw fail(CoreException.Code.INVALID_PACKAGE,"Integer field required: "+key);return (Long)value;
  }
  static String sha(byte[] bytes){return hex(digest().digest(bytes));}
  private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
  private static String hashFile(Path path) throws IOException {
    MessageDigest digest=digest();try(InputStream in=Files.newInputStream(path)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);}return hex(digest.digest());
  }
  private static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte value:bytes)b.append(String.format(Locale.ROOT,"%02x",value&255));return b.toString();}
  private static CoreException fail(CoreException.Code code,String detail){return new CoreException(code,detail);}
  private static void removeStage(Path dir) throws IOException {
    // No symlink following, and never called for an installed package ID.
    if(Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS))try(DirectoryStream<Path> entries=Files.newDirectoryStream(dir)){for(Path p:entries)removeStage(p);}
    Files.deleteIfExists(dir);
  }
}
