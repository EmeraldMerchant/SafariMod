package com.safari.mod.render;

import com.safari.mod.SafariModClient;
import com.safari.mod.util.ModScanner;
import com.safari.mod.config.ConfigManager;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;

public final class TextDisplayManager {

    private TextDisplayManager() {
    }

    private static final int BIOME_COUNT = 4;
    private static final List<List<String>> DEFAULT_BIOMES = List.of(
            new ArrayList<>(Arrays.asList(
                    "§6§lCavern Biome§r",
                    "§fCavernfish",
                    "§fShyworm",
                    "§fFlitter",
                    "§aDriftling",
                    "§9Chuckwalla",
                    "§9Rockmite",
                    "§9Scrappy",
                    "§9Snoozle",
                    "§5Gemzie")),

            new ArrayList<>(Arrays.asList(
                    "§2§lForest Biome§r",
                    "§fFoxtrot",
                    "§aHoneybug",
                    "§aTreefrog",
                    "§aWoodchucker",
                    "§aBluebird",
                    "§9Fluffling",
                    "§9Hideonfloor",
                    "§9Parakeet",
                    "§6Macaw")),

            new ArrayList<>(Arrays.asList(
                    "§5§lHaunted Biome§r",
                    "§aAreita",
                    "§aBloodbat",
                    "§aDuplico",
                    "§aGazer",
                    "§aLitterbug",
                    "§aSolsnatcher",
                    "§9Gimmiegold",
                    "§9Hideonwall",
                    "§9Hideyho",
                    "§6Doomspiral")),

            new ArrayList<>(Arrays.asList(
                    "§b§lIcy Biome§r",
                    "§fStrongarm",
                    "§fTepid",
                    "§aPolaris",
                    "§aShuddersquid",
                    "§9Billygoat",
                    "§9Mantis Shrimp",
                    "§9Nozzlenose",
                    "§9Troodon",
                    "§6Wumpa")));

    private static final List<List<String>> biomes = new ArrayList<>();

    public static int getLocationX() {
        return ConfigManager.getLocationX();
    }

    public static int getLocationY() {
        return ConfigManager.getLocationY();
    }

    private static boolean initialized = false;

    public static void init() {

        if (initialized) {
            return;
        }

        initialized = true;

        reset();

        registerCommand();
    }

    public static void reset() {

        biomes.clear();

        for (List<String> biome : DEFAULT_BIOMES) {
            biomes.add(
                    new ArrayList<>(biome));
        }
    }

    public static void render(
            GuiGraphicsExtractor graphics,
            DeltaTracker deltaTracker) {

        Minecraft minecraft = Minecraft.getInstance();

        if (!SafariModClient.inSafari) {
            return;
        }

        int locationX = ConfigManager.getLocationX();

        int locationY = ConfigManager.getLocationY();

        int columnWidth = 90;

        for (int biomeIndex = 0; biomeIndex < biomes.size(); biomeIndex++) {

            List<String> biome = biomes.get(biomeIndex);

            int x = locationX
                    + biomeIndex * columnWidth;

            int y = locationY;

            for (String text : biome) {

                if (text == null ||
                        text.isEmpty()) {
                    continue;
                }

                graphics.text(
                        minecraft.font,
                        Component.literal(text),
                        x,
                        y,
                        0xFFFFFFFF,
                        true);

                y += minecraft.font.lineHeight;
            }
        }
    }

    public static void setText(
            int biomeIndex,
            String text) {

        checkBiomeIndex(biomeIndex);

        List<String> lines = new ArrayList<>(
                Arrays.asList(
                        text.split(
                                "\\R",
                                -1)));

        biomes.set(
                biomeIndex,
                lines);
    }

    public static String getText(
            int biomeIndex) {

        checkBiomeIndex(biomeIndex);

        return String.join(
                "\n",
                biomes.get(biomeIndex));
    }

    public static String getLine(
            int biomeIndex,
            int line) {

        checkBiomeIndex(biomeIndex);

        List<String> lines = biomes.get(biomeIndex);

        if (line < 0 || line >= lines.size()) {
            return "";
        }

        return lines.get(line);
    }

    public static void setLine(
            int biomeIndex,
            int line,
            String text) {

        checkBiomeIndex(biomeIndex);

        if (line < 0) {
            return;
        }

        List<String> lines = biomes.get(biomeIndex);

        while (lines.size() <= line) {
            lines.add("");
        }

        lines.set(
                line,
                text == null ? "" : text);
    }

    public static void clear(
            int biomeIndex) {

        checkBiomeIndex(biomeIndex);

        biomes.get(biomeIndex).clear();
    }

    public static void clear() {

        for (List<String> biome : biomes) {
            biome.clear();
        }
    }

    public static void setLocation(
            int x,
            int y) {

        ConfigManager.setLocation(x, y);
    }

    public static boolean removeMob(String mobName) {

        if (mobName == null) {
            return false;
        }

        String cleanTarget = ModScanner.cleanText(mobName).trim();

        if (cleanTarget.isEmpty()) {
            return false;
        }

        for (List<String> biome : biomes) {

            // line 0 is biome title
            for (int line = 1; line < biome.size(); line++) {

                String cleanLine = ModScanner.cleanText(
                        biome.get(line)).trim();

                if (cleanLine.equalsIgnoreCase(cleanTarget)) {

                    biome.remove(line);

                    return true;
                }
            }
        }

        return false;
    }

    public static boolean containsMob(String mobName) {

        if (mobName == null) {
            return false;
        }

        String cleanTarget = ModScanner.cleanText(mobName).trim();

        if (cleanTarget.isEmpty()) {
            return false;
        }

        for (List<String> biome : biomes) {

            for (int line = 1; line < biome.size(); line++) {

                String cleanLine = ModScanner.cleanText(
                        biome.get(line)).trim();

                if (cleanLine.equalsIgnoreCase(cleanTarget)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void checkBiomeIndex(
            int biomeIndex) {

        if (biomeIndex < 0 ||
                biomeIndex >= BIOME_COUNT) {
            throw new IndexOutOfBoundsException(
                    "Biome index must be 0-3");
        }
    }

    private static void registerCommand() {

        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, buildContext) -> {

                    dispatcher.register(
                            ClientCommands.literal("setlocation")
                                    .then(
                                            ClientCommands.argument(
                                                    "x",
                                                    integer())
                                                    .then(
                                                            ClientCommands.argument(
                                                                    "y",
                                                                    integer())
                                                                    .executes(context -> {

                                                                        int x = getInteger(
                                                                                context,
                                                                                "x");

                                                                        int y = getInteger(
                                                                                context,
                                                                                "y");

                                                                        setLocation(
                                                                                x,
                                                                                y);

                                                                        context
                                                                                .getSource()
                                                                                .sendFeedback(
                                                                                        Component.literal(
                                                                                                "Safari display location set to "
                                                                                                        + x
                                                                                                        + ", "
                                                                                                        + y));

                                                                        return 1;
                                                                    }))));
                });
    }
}