package net.ryzlar;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.ryzlar.entities.ModEntities;
import net.ryzlar.items.effects.ArmPiece;
import net.ryzlar.items.ModComponents;
import net.ryzlar.items.ModItems;
import net.ryzlar.network.ArmPiecePayload;
import net.ryzlar.network.StrategemBallThrowPayload;
import net.ryzlar.network.StrategemBallThrowHandler;
import net.ryzlar.network.StrategemProgramPayload;
import net.ryzlar.network.StrategemCooldownPayload;
import net.ryzlar.strategem.StrategemCommands;
import net.ryzlar.strategem.StrategemCooldowns;
import net.ryzlar.strategem.Strategems;
import net.ryzlar.sound.ModSounds;
import net.ryzlar.tabs.ModTab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class LaserMod implements ModInitializer {
	public static String MOD_ID = "lasermod";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

//	public String getModId() {
//		return MOD_ID;
//	}

	@Override
	public void onInitialize() {

		LOGGER.info("debugging " + MOD_ID + " mod...");

		PayloadTypeRegistry.playS2C().register(ArmPiecePayload.TYPE, ArmPiecePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(StrategemCooldownPayload.TYPE, StrategemCooldownPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(StrategemBallThrowPayload.TYPE, StrategemBallThrowPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(StrategemProgramPayload.TYPE, StrategemProgramPayload.CODEC);

		ModSounds.initialize();
		ModComponents.initialize();
		Strategems.initialize();
		ModItems.initialize();
		ModTab.initialize();

		ModEntities.initialize();

		ArmPiece.initialize();

		StrategemBallThrowHandler.register();
		StrategemCooldowns.initialize();
		StrategemCommands.initialize();

	}
}