package EXT.DOMAIN.cpe.datetime.jackson;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.util.VersionUtil;

public class ModuleVersion extends VersionUtil {
    public final static ModuleVersion instance = new ModuleVersion();

    public Version version() {
        return versionFor(ModuleVersion.class);
    }
}
