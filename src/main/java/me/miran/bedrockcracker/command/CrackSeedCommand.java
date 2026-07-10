package me.miran.bedrockcracker.command;

import com.mojang.brigadier.CommandDispatcher;
import me.miran.bedrockcracker.BedrockCracker;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;

public class CrackSeedCommand implements ClientCommandRegistrationCallback {

    @Override
    public void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register((ClientCommands.literal("crackseed")).executes((context)-> run()));
    }

    private int run() {
        new Thread(BedrockCracker::crackWorldSeed).start();

        return 0;
    }
}