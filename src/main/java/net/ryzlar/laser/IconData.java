package net.ryzlar.laser;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.ryzlar.LaserMod;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;


public  class IconData {

    public static Identifier IdentifierPayload;


    public IconData(Identifier packet) {

        IdentifierPayload = packet;

    }

}
