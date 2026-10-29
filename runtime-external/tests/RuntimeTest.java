package dev.geckolite.runtime;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.util.zip.*;

/** Real filesystem/signature tests: no Android mocks or implementation mirrors. */
public final class RuntimeTest {
  private static int tests;
  private static void check(boolean value) { if (!value) throw new AssertionError(); }
  private static void rejects(String code, RunnableChecked action) throws Exception {
    try { action.run(); throw new AssertionError("Expected " + code); }
    catch (CoreException error) { check(error.code.name().equals(code)); ++tests; }
  }
  interface RunnableChecked { void run() throws Exception; }
  public static void main(String[] args) throws Exception {
    Path pair=Paths.get(args[0]), root=Paths.get(args[2]);Files.createDirectories(root);
    byte[] contract=Files.readAllBytes(pair.resolve("host-contract.json"));
    PublicKey key=KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Files.readAllBytes(Paths.get(args[1]))));
    Map<String,PublicKey> keys=Collections.singletonMap("local-test",key);
    String id=(String)StrictJson.object(contract).get("manifestSha256");
    rejects("CORE_NOT_INSTALLED",()->CorePackages.validateInstalled(root,id,contract,keys));
    try(InputStream in=Files.newInputStream(pair.resolve("core-package.zip"))) {
      check(CorePackages.install(root,in,contract,keys).equals(id));++tests;
    }
    Path core=CorePackages.validateInstalled(root,id,contract,keys);
    check(Files.size(core)>0);++tests;
    try(InputStream in=Files.newInputStream(pair.resolve("core-package.zip"))) {
      check(CorePackages.install(root,in,contract,keys).equals(id));++tests;
    }
    Map<String,PublicKey> wrong=Collections.singletonMap("local-test",KeyPairGenerator.getInstance("EC").generateKeyPair().getPublic());
    rejects("INVALID_SIGNATURE",()->{try(InputStream in=Files.newInputStream(pair.resolve("core-package.zip"))){CorePackages.install(root,in,contract,wrong);}});
    byte[] badContract=new String(contract,java.nio.charset.StandardCharsets.UTF_8).replace("lite-zh-CN-no-glean-no-pdf-speech","other-profile").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    rejects("CORE_INCOMPATIBLE",()->{try(InputStream in=Files.newInputStream(pair.resolve("core-package.zip"))){CorePackages.install(root,in,badContract,keys);}});
    byte[] original=Files.readAllBytes(core);
    // A failed installation must not overwrite an already verified version.
    Path damaged=root.resolve("damaged.zip");
    try(ZipInputStream in=new ZipInputStream(Files.newInputStream(pair.resolve("core-package.zip")));ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(damaged))){
      ZipEntry e;while((e=in.getNextEntry())!=null){out.putNextEntry(new ZipEntry(e.getName()));byte[] bytes=in.readAllBytes();if(e.getName().equals("core.apk"))bytes[0]^=1;out.write(bytes);out.closeEntry();}
    }
    rejects("CORE_CORRUPT",()->{try(InputStream in=Files.newInputStream(damaged)){CorePackages.install(root,in,contract,keys);}});
    check(Arrays.equals(original,Files.readAllBytes(CorePackages.validateInstalled(root,id,contract,keys))));++tests;
    Path unsafe=root.resolve("unsafe.zip");
    try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(unsafe))){out.putNextEntry(new ZipEntry("../escape"));out.write(1);out.closeEntry();}
    rejects("INVALID_PACKAGE",()->{try(InputStream in=Files.newInputStream(unsafe)){CorePackages.install(root,in,contract,keys);}});
    check(!Files.exists(root.getParent().resolve("escape")));++tests;
    Path oversized=root.resolve("oversized.zip");
    try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(oversized))){
      for(String name:Arrays.asList("manifest.json","manifest.sig","core.apk")){
        out.putNextEntry(new ZipEntry(name));out.write(new byte[name.equals("manifest.json")?65537:1]);out.closeEntry();
      }
    }
    rejects("INVALID_PACKAGE",()->{try(InputStream in=Files.newInputStream(oversized)){CorePackages.install(root,in,contract,keys);}});
    rejects("INVALID_SIGNATURE",()->{try(InputStream in=Files.newInputStream(pair.resolve("core-package.zip"))){CorePackages.install(root,in,contract,Collections.emptyMap());}});
    rejects("IO_ERROR",()->{try(InputStream source=Files.newInputStream(pair.resolve("core-package.zip"));
        InputStream interrupted=new FilterInputStream(source) {
          int remaining=1024;
          @Override public int read(byte[] bytes,int offset,int count) throws IOException {
            if(remaining<=0)throw new IOException("Controlled interrupted local input");
            int n=super.read(bytes,offset,Math.min(count,remaining));if(n>0)remaining-=n;return n;
          }
        }) {CorePackages.install(root,interrupted,contract,keys);}});
    CorePackages.validateInstalled(root,id,contract,keys);++tests;
    check(!Files.getPosixFilePermissions(core).contains(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));++tests;
    rejects("INVALID_PACKAGE",()->StrictJson.object("{\"a\":1,\"a\":2}".getBytes()));
    rejects("INVALID_PACKAGE",()->StrictJson.object("{\"a\":1.5}".getBytes()));
    rejects("INVALID_PACKAGE",()->StrictJson.object(new byte[]{'{','"','a','"',':','"',(byte)0xff,'"','}'}));
    rejects("INVALID_PACKAGE",()->StrictJson.object("{\"a\":1} trailing".getBytes()));
    rejects("INVALID_PACKAGE",()->StrictJson.object("{\"a\":9223372036854775808}".getBytes()));
    rejects("INVALID_PACKAGE",()->CorePackages.validateInstalled(root,"../../escape",contract,keys));
    check(Files.list(root).noneMatch(p->p.getFileName().toString().startsWith(".stage-")));++tests;
    // A valid re-import must repair an incomplete published directory.
    Files.delete(root.resolve(id).resolve("lease"));
    try(InputStream input=Files.newInputStream(pair.resolve("core-package.zip"))) {
      check(CorePackages.install(root,input,contract,keys).equals(id));++tests;
    }
    CorePackages.validateInstalled(root,id,contract,keys);++tests;
    Files.delete(root.resolve(id).resolve("manifest.json"));
    try(InputStream input=Files.newInputStream(pair.resolve("core-package.zip"))) {
      check(CorePackages.install(root,input,contract,keys).equals(id));++tests;
    }
    CorePackages.validateInstalled(root,id,contract,keys);++tests;
    System.out.println("PASS: "+tests+" real package/install/signature/strict JSON checks");
  }
}
