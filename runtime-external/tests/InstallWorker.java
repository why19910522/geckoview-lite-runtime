package dev.geckolite.runtime;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
/** Separate JVMs exercise the actual filesystem lock and atomic publication. */
public final class InstallWorker {
 public static void main(String[] args) throws Exception {
  Path pair=Paths.get(args[0]),root=Paths.get(args[2]);
  byte[] contract=Files.readAllBytes(pair.resolve("host-contract.json"));
  PublicKey key=KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Files.readAllBytes(Paths.get(args[1]))));
  Map<String,PublicKey> keys=Collections.singletonMap("local-test",key);
  String expected=(String)StrictJson.object(contract).get("manifestSha256");
  if(args.length==3) try(InputStream in=Files.newInputStream(pair.resolve("core-package.zip"))) {
   if(!CorePackages.install(root,in,contract,keys).equals(expected))throw new AssertionError();
  }
  CorePackages.validateInstalled(root,expected,contract,keys);
  if(args.length>3) try(java.util.stream.Stream<Path> entries=Files.list(root)) {
   if(entries.anyMatch(p->p.getFileName().toString().startsWith(".stage-")))throw new AssertionError();
  }
  System.out.println(expected);
 }
}
