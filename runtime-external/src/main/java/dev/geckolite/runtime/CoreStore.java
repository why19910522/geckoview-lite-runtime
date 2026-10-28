package dev.geckolite.runtime;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.Build;
import java.io.*;
import java.nio.file.*;
import java.security.PublicKey;
import java.util.*;
import org.mozilla.gecko.mozglue.GeckoLoader;

/** Optional local installer. Call install/prepare on a worker before creating GeckoRuntime. */
public final class CoreStore {
  public enum State { ABSENT, INSTALLED, PREPARED }
  private final Context context;
  private final Path root;
  private final byte[] contract;
  private final Map<String,PublicKey> keys;
  private final String expectedId;
  private volatile String prepared;

  public CoreStore(Context context, Map<String,PublicKey> trustedKeys) throws CoreException {
    this.context=context.getApplicationContext();
    root=this.context.getNoBackupFilesDir().toPath().resolve("geckolite-cores");
    keys=Collections.unmodifiableMap(new HashMap<>(trustedKeys));
    try(InputStream in=this.context.getAssets().open("geckolite/host-contract.json")) {
      ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[4096];int n;
      while((n=in.read(bytes))!=-1){if(out.size()+n>65536)throw new IOException("Contract too large");out.write(bytes,0,n);}contract=out.toByteArray();
      Map<String,Object> parsed=StrictJson.object(contract);
      expectedId=CorePackages.string(parsed,"manifestSha256");
      if(Build.VERSION.SDK_INT<CorePackages.number(parsed,"minApi")||!Arrays.asList(Build.SUPPORTED_ABIS).contains(CorePackages.string(parsed,"abi")))
        throw new CoreException(CoreException.Code.CORE_INCOMPATIBLE,"Unsupported device API/ABI");
    } catch(IOException e){throw new CoreException(CoreException.Code.CORE_INCOMPATIBLE,"No external bridge contract",e);}
  }
  public String expectedPackageId(){return expectedId;}
  public String install(InputStream localBytes) throws CoreException {
    return CorePackages.install(root,localBytes,contract,keys);
  }
  public synchronized void prepare(String packageId) throws CoreException {
    if(prepared!=null&&!prepared.equals(packageId))throw new CoreException(CoreException.Code.CORE_RESTART_REQUIRED,"Process already prepared");
    Path core=CorePackages.validateInstalled(root,packageId,contract,keys);
    try(ParcelFileDescriptor data=ParcelFileDescriptor.open(core.toFile(),ParcelFileDescriptor.MODE_READ_ONLY);
        ParcelFileDescriptor lease=ParcelFileDescriptor.open(CorePackages.leasePath(root,packageId).toFile(),ParcelFileDescriptor.MODE_READ_ONLY)) {
      GeckoLoader.prepareExternalCore(context,packageId,data,lease);prepared=packageId;
    } catch(IOException e){throw new CoreException(CoreException.Code.IO_ERROR,"Cannot open installed package",e);}
    catch(IllegalStateException e){throw new CoreException("CORE_RESTART_REQUIRED".equals(e.getMessage())?CoreException.Code.CORE_RESTART_REQUIRED:CoreException.Code.CORE_LOAD_UNSUPPORTED,"Native bootstrap selection rejected",e);}
  }
  public State queryState() throws CoreException {
    if(expectedId.equals(prepared))return State.PREPARED;
    if(!Files.exists(root.resolve(expectedId),LinkOption.NOFOLLOW_LINKS))return State.ABSENT;
    CorePackages.validateInstalled(root,expectedId,contract,keys);return State.INSTALLED;
  }
  public String capabilityProfile() throws CoreException {
    return CorePackages.string(StrictJson.object(contract),"profile");
  }
}
