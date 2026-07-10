package me.miran.bedrockcracker.util;

import net.minecraft.client.Minecraft;

public class WorldHelper {

    public static Dimension getDimension() {
        if (Minecraft.getInstance().level == null) return null;

        String dimensionID = Minecraft.getInstance().level.dimension().toString();

        switch (dimensionID){
            case "ResourceKey[minecraft:dimension / minecraft:overworld]" -> {
                return Dimension.OVERWORLD;
            }
            case "ResourceKey[minecraft:dimension / minecraft:the_nether]" -> {
                return Dimension.NETHER;
            }
            case "ResourceKey[minecraft:dimension / minecraft:the_end]" -> {
                return Dimension.END;
            }
            default -> {
                return null;
            }
        }
    }

    public enum Dimension {
        OVERWORLD,
        NETHER,
        END
    }


}
