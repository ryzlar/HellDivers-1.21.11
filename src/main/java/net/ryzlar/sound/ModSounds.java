package net.ryzlar.sound;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.ryzlar.LaserMod;

/**
 * The Helldivers equipment sound kit (synthesized, see assets/lasermod/sounds). One shared voice for the
 * Arm Piece, the stratagem HUD and the beacon, so they sound like parts of the same system.
 */
public final class ModSounds {

    public static final SoundEvent UPLINK_OPEN = register("uplink.open");
    public static final SoundEvent UPLINK_CLOSE = register("uplink.close");
    public static final SoundEvent INPUT = register("uplink.input");
    public static final SoundEvent INPUT_ERROR = register("uplink.error");
    public static final SoundEvent PAGE = register("uplink.page");
    public static final SoundEvent STRATAGEM_READY = register("uplink.ready");
    public static final SoundEvent PROGRAM_UPLOAD = register("beacon.program_upload");
    public static final SoundEvent PROGRAM_COMPLETE = register("beacon.program_complete");
    public static final SoundEvent BEACON_ARM = register("beacon.arm");
    public static final SoundEvent BEACON_LAND = register("beacon.land");

    // Stratagem attacks: layered warning / build-up / impact / aftermath sounds
    public static final SoundEvent RIFT_WARNING = register("strike.rift_warning");
    public static final SoundEvent RIFT_TEAR = register("strike.rift_tear");
    public static final SoundEvent RIFT_OPEN = register("strike.rift_open");
    public static final SoundEvent RIFT_LOOP = register("strike.rift_loop");
    public static final SoundEvent RIFT_UNSTABLE = register("strike.rift_unstable");
    public static final SoundEvent RIFT_CLOSE = register("strike.rift_close");
    public static final SoundEvent RIFT_SNAP = register("strike.rift_snap");
    public static final SoundEvent METEOR_WARNING = register("strike.meteor_warning");
    public static final SoundEvent METEOR_INCOMING = register("strike.meteor_incoming");
    public static final SoundEvent METEOR_IMPACT = register("strike.meteor_impact");
    public static final SoundEvent DEBRIS_RAIN = register("strike.debris_rain");
    public static final SoundEvent LIGHTNING_CHARGE = register("strike.lightning_charge");
    public static final SoundEvent LIGHTNING_ZAP = register("strike.lightning_zap");
    public static final SoundEvent LIGHTNING_STRIKE = register("strike.lightning_strike");
    public static final SoundEvent LIGHTNING_FINAL_CHARGE = register("strike.lightning_final_charge");
    public static final SoundEvent LIGHTNING_DISCHARGE = register("strike.lightning_discharge");
    public static final SoundEvent LIGHTNING_CRACKLE = register("strike.lightning_crackle");
    public static final SoundEvent VOLCANO_RUMBLE = register("strike.volcano_rumble");
    public static final SoundEvent VOLCANO_CRACK = register("strike.volcano_crack");
    public static final SoundEvent VOLCANO_VENT = register("strike.volcano_vent");
    public static final SoundEvent VOLCANO_ERUPTION = register("strike.volcano_eruption");
    public static final SoundEvent VOLCANO_BURST = register("strike.volcano_burst");
    public static final SoundEvent STEAM_HISS = register("strike.steam_hiss");
    public static final SoundEvent NUKE_ALARM = register("strike.nuke_alarm");
    public static final SoundEvent NUKE_CHARGE = register("strike.nuke_charge");
    public static final SoundEvent NUKE_DETONATION = register("strike.nuke_detonation");
    public static final SoundEvent NUKE_SHOCKWAVE = register("strike.nuke_shockwave");
    public static final SoundEvent NUKE_AFTERMATH = register("strike.nuke_aftermath");

    private ModSounds() {
    }

    private static SoundEvent register(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    /** Forces class loading so the events are registered during mod init. */
    public static void initialize() {
    }
}
