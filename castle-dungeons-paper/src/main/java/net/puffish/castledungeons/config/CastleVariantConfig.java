package net.puffish.castledungeons.config;

import java.util.List;

/**
 * Configuration for one castle variant (e.g. plains or desert) loaded from config.yml.
 */
public record CastleVariantConfig(
        String name,
        boolean enabled,
        int minSize,
        int maxSize,
        double mossiness,
        double crackiness,
        List<String> biomes,
        List<RoomTemplateConfig> rooms
) {}
