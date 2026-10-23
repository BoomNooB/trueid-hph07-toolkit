package local.tv.tools;
import java.lang.reflect.Method;
/** One-shot uninstall protection for this session's own launcher only. Never uninstalls anything. */
public final class PackageGuard {
    private static final String PACKAGE = "com.gathin.tvhome";
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !(args[0].equals("get") || args[0].equals("true") || args[0].equals("false")))
            throw new IllegalArgumentException("Use get, true or false for the local launcher only");
        Object manager = Class.forName("android.app.AppGlobals").getMethod("getPackageManager").invoke(null);
        Class<?> api = Class.forName("android.content.pm.IPackageManager");
        Method get = api.getMethod("getBlockUninstallForUser", String.class, int.class);
        System.out.println("before=" + get.invoke(manager, PACKAGE, 0));
        if (!args[0].equals("get")) {
            boolean requested = Boolean.parseBoolean(args[0]);
            Method set = api.getMethod("setBlockUninstallForUser", String.class, boolean.class, int.class);
            System.out.println("writeResult=" + set.invoke(manager, PACKAGE, requested, 0));
            Object after = get.invoke(manager, PACKAGE, 0);
            System.out.println("after=" + after);
            if (!Boolean.valueOf(requested).equals(after)) throw new IllegalStateException("Read-back mismatch");
        }
    }
}
