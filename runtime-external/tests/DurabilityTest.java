package dev.geckolite.runtime;
import java.io.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
/** Real temporary files/channels plus an injectable fsync failure boundary. */
public final class DurabilityTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args) throws Exception {
  Path pair=Paths.get(args[0]),root=Paths.get(args[2]);byte[] contract=Files.readAllBytes(pair.resolve("host-contract.json"));
  String id=(String)StrictJson.object(contract).get("manifestSha256");
  Map<String,PublicKey> keys=Collections.singletonMap("local-test",KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Files.readAllBytes(Paths.get(args[1])))));
  Set<String> files=new HashSet<>();boolean[] stage={false},published={false},parent={false};
  CorePackages.Durability durable=(path,channel)->{
   if(Files.isDirectory(path)) {
    if(path.equals(root.getParent()))parent[0]=true;
    if(path.getFileName().toString().startsWith(".stage-")&&!path.getFileName().toString().startsWith(".stage-repair-")) {
     check(files.containsAll(Arrays.asList("core.apk","manifest.json","manifest.sig","lease")));
     check(!Files.exists(root.resolve(id)));stage[0]=true;
    }
    if(path.equals(root)&&Files.isDirectory(root.resolve(id))) {
     check(stage[0]&&parent[0]);published[0]=true;
     // A directory fsync error after rename must never report success.
     throw new IOException("Controlled parent-directory fsync error");
    }
   } else if(path.getParent().getFileName().toString().startsWith(".stage-")) {
    files.add(path.getFileName().toString());
    if(!path.getFileName().toString().equals("lease"))check(!Files.getPosixFilePermissions(path).contains(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
   }
   channel.force(true);
  };
  try(InputStream in=Files.newInputStream(pair.resolve("core-package.zip"))) {
   try {CorePackages.install(root,in,contract,keys,durable);throw new AssertionError("fsync error reported success");}
   catch(CoreException e){check(e.code==CoreException.Code.IO_ERROR);}
  }
  check(stage[0]&&published[0]&&parent[0]);
  try(InputStream in=Files.newInputStream(pair.resolve("core-package.zip"))) {check(CorePackages.install(root,in,contract,keys).equals(id));}
  CorePackages.validateInstalled(root,id,contract,keys);
  check(Files.isRegularFile(root.resolve(".lease-"+id))&&Files.isRegularFile(root.resolve(id).resolve("lease")));
  Object stable=Files.readAttributes(root.resolve(".lease-"+id),java.nio.file.attribute.BasicFileAttributes.class).fileKey();
  // Incomplete target recovery must respect a process holding the stable lease.
  Files.delete(root.resolve(id).resolve("manifest.sig"));
  try(FileChannel held=FileChannel.open(root.resolve(".lease-"+id),StandardOpenOption.READ,StandardOpenOption.WRITE);FileLock lock=held.lock(0,Long.MAX_VALUE,true);
      InputStream in=Files.newInputStream(pair.resolve("core-package.zip"))) {
   try {CorePackages.install(root,in,contract,keys);throw new AssertionError("Repair bypassed active lease");}
   catch(CoreException e){check(e.code==CoreException.Code.CORE_IN_USE);}
  }
  try(InputStream in=Files.newInputStream(pair.resolve("core-package.zip"))) {check(CorePackages.install(root,in,contract,keys).equals(id));}
  CorePackages.validateInstalled(root,id,contract,keys);
  check(Objects.equals(stable,Files.readAttributes(root.resolve(".lease-"+id),java.nio.file.attribute.BasicFileAttributes.class).fileKey()));
  System.out.println("PASS: final file permissions/lease→stage→rename→root/parent fsync ordering, sync failure propagation, retry and leased repair guard");
 }
}
