package dev.geckolite.smoke;
import android.content.Context;
import android.util.Log;
import dev.geckolite.runtime.*;
import java.io.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
final class CoreBootstrap {
    static void prepare(Context context, boolean install) throws Exception {
        byte[] key;
        try(InputStream in=context.getAssets().open("probe-public.der");ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);key=out.toByteArray();
        }
        PublicKey publicKey=KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(key));
        CoreStore store=new CoreStore(context, Collections.singletonMap("local-test",publicKey));
        Log.i("GeckoExternalProbe","STATE="+store.queryState());
        if(install) {
            File source=new File(context.getFilesDir(),"incoming-core.zip");
            try(InputStream input=new FileInputStream(source)) {
                String id=store.install(input);Log.i("GeckoExternalProbe","INSTALLED id="+id);
            }
            if(!source.delete())throw new IOException("Cannot remove test import");
        }
        store.prepare(store.expectedPackageId());
        store.prepare(store.expectedPackageId()); // Repeated selection must be idempotent.
        try {store.prepare("0000000000000000000000000000000000000000000000000000000000000000");
            throw new IOException("Prepared process accepted another package");
        } catch(CoreException expected) {
            if(expected.code!=CoreException.Code.CORE_RESTART_REQUIRED)throw expected;
        }
        Log.i("GeckoExternalProbe","DIFFERENT_PACKAGE_REJECTED");
        // Use an independently opened description: dup must not release an OFD lease.
        File lease=new File(context.getNoBackupFilesDir(),"geckolite-cores/"+store.expectedPackageId()+"/lease");
        java.lang.reflect.Method lock=Class.forName("org.mozilla.gecko.mozglue.ExternalCore").getDeclaredMethod("nativeLockLease",int.class,boolean.class);
        lock.setAccessible(true);
        try(android.os.ParcelFileDescriptor test=android.os.ParcelFileDescriptor.open(lease,android.os.ParcelFileDescriptor.MODE_READ_WRITE)) {
            assertLeaseBusy(lock,test.getFd());
            try(android.os.ParcelFileDescriptor duplicate=android.os.ParcelFileDescriptor.dup(test.getFileDescriptor())) { }
            assertLeaseBusy(lock,test.getFd());
        }
        Log.i("GeckoExternalProbe","LEASE_HELD_AFTER_DUP_CLOSE");
        int slot;
        try(InputStream input=context.getAssets().open("geckolite/host-contract.json");ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1)out.write(buffer,0,n);
            slot=new org.json.JSONObject(out.toString("UTF-8")).getInt("resourceFd");
        }
        java.lang.reflect.Method reserve=Class.forName("org.mozilla.gecko.mozglue.ExternalCore").getDeclaredMethod("nativeReserveFd",int.class,int.class);
        reserve.setAccessible(true);
        try(android.os.ParcelFileDescriptor input=android.os.ParcelFileDescriptor.open(lease,android.os.ParcelFileDescriptor.MODE_READ_ONLY)) {
            if((Integer)reserve.invoke(null,input.getFd(),slot)!=-android.system.OsConstants.EBUSY)
                throw new IOException("Resource FD collision was not rejected");
        }
        Log.i("GeckoExternalProbe","FD_COLLISION_REJECTED_WITHOUT_OVERWRITE");
        java.io.FileDescriptor writable=android.system.Os.open("/proc/self/fd/"+slot,android.system.OsConstants.O_RDWR|android.system.OsConstants.O_CLOEXEC,0);
        try {
            try {android.system.Os.pwrite(writable,new byte[]{0},0,1,0);throw new IOException("Sealed core accepted write");}
            catch(android.system.ErrnoException expected) {if(expected.errno!=android.system.OsConstants.EPERM)throw expected;}
        } finally {android.system.Os.close(writable);}
        Log.i("GeckoExternalProbe","SEALED_CORE_WRITE_REJECTED");
        Log.i("GeckoExternalProbe","PREPARED id="+store.expectedPackageId()+" profile="+store.capabilityProfile());
    }
    private static void assertLeaseBusy(java.lang.reflect.Method method,int fd) throws Exception {
        int error=(Integer)method.invoke(null,fd,true);
        if(error!=android.system.OsConstants.EAGAIN && error!=android.system.OsConstants.EACCES)
            throw new IOException("Lease not held: errno="+error);
    }
}
