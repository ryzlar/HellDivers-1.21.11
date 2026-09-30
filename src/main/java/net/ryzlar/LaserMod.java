package net.ryzlar;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.ryzlar.entities.ModEntities;
import net.ryzlar.items.effects.ArmPiece;
import net.ryzlar.items.ModItems;
import net.ryzlar.network.ArmPiecePayload;
import net.ryzlar.network.BeamPayload;
import net.ryzlar.network.StrategemBallThrowPayload;
import net.ryzlar.network.StrategemBallThrowHandler;
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

		PayloadTypeRegistry.playS2C().register(BeamPayload.TYPE, BeamPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ArmPiecePayload.TYPE, ArmPiecePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(StrategemBallThrowPayload.TYPE, StrategemBallThrowPayload.CODEC);

		ModItems.initialize();
		ModTab.initialize();

		ModEntities.initialize();

		ArmPiece.initialize();

		StrategemBallThrowHandler.register();

	}
}