package no.rutebanken.marduk.netex;

import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;

/** A {@link NetexDsjExportConfig} as {@code src/test/resources/application.properties} configures it. */
public final class NetexDsjTestConfig {

    private NetexDsjTestConfig() {
    }

    public static NetexDsjExportConfig create(Path downloadDirectory) {
        return create(downloadDirectory, true, true);
    }

    public static NetexDsjExportConfig create(Path downloadDirectory, boolean enabled, boolean upgradeEnabled) {
        NetexDsjExportConfig config = new NetexDsjExportConfig();
        ReflectionTestUtils.setField(config, "enabled", enabled);
        ReflectionTestUtils.setField(config, "defaultVariantName", "legacy");
        ReflectionTestUtils.setField(config, "apiDefaultVariantName", "legacy");
        ReflectionTestUtils.setField(config, "legacyBlobPath", "outbound/netex-dsj-legacy/");
        ReflectionTestUtils.setField(config, "newBlobPath", "outbound/netex-dsj-new/");
        ReflectionTestUtils.setField(config, "downloadDirectory", downloadDirectory.toString());
        ReflectionTestUtils.setField(config, "maxXsltFileSizeMb", 300L);
        // rut is included so that the test datasets of rb_rut exercise the downgrade
        ReflectionTestUtils.setField(config, "dsjCodespaces", List.of("RUT", "VYG", "GOA", "SJN"));
        ReflectionTestUtils.setField(config, "upgradeEnabled", upgradeEnabled);
        config.resolveConfiguredVariants();
        return config;
    }

    /** The dual export and the upgrade both switched off, as every pipeline test that is not about them wants it. */
    public static NetexDsjExportConfig disabled() {
        return create(Path.of("target/files/netex/dsj"), false, false);
    }

    /** Touches nothing while the dual export is disabled, so it needs no blob stores. */
    public static NetexDsjExport disabledExport() {
        return new NetexDsjExport(disabled(), null, null, null);
    }

    /** Touches nothing while the upgrade is disabled, so it needs no blob store. */
    public static NetexDsjUpgrade disabledUpgrade() {
        return new NetexDsjUpgrade(disabled(), null, null, null);
    }
}
