package client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Provides a built-in word list so no network fetch is needed.
 */
public class WordList {

    private static final String[] WORDS = {
        "apple", "banana", "guitar", "castle", "dragon", "forest", "penguin", "umbrella",
        "volcano", "diamond", "candle", "bridge", "camera", "dolphin", "elephant", "feather",
        "giraffe", "hammer", "island", "jigsaw", "ketchup", "lantern", "mustard", "noodle",
        "octopus", "parrot", "quilt", "rainbow", "sandal", "telescope", "unicorn", "vampire",
        "waterfall", "xylophone", "yogurt", "zebra", "anchor", "balloon", "cactus", "dagger",
        "eagle", "flame", "ghost", "honey", "igloo", "juggler", "koala", "lemon", "magnet",
        "nurse", "orange", "planet", "queen", "rocket", "sailboat", "tornado", "urchin",
        "vase", "wizard", "yarn", "zipper", "acorn", "beaver", "coral", "donut", "emerald",
        "fossil", "glacier", "helmet", "ivy", "javelin", "kettle", "lobster", "mushroom",
        "napkin", "orchid", "peacock", "quicksand", "radish", "snorkel", "trombone", "ukulele",
        "villain", "walrus", "xenon", "yolk", "zucchini", "astronaut", "binoculars", "compass",
        "dinosaur", "envelope", "firefly", "gondola", "hourglass", "icicle", "jellyfish",
        "knight", "lighthouse", "marmalade", "nightingale", "overalls", "porcupine", "quarry",
        "rhinoceros", "submarine", "toadstool", "umbrella", "vortex", "windmill", "xray",
        "yesterday", "zeppelin", "abacus", "blizzard", "chimney", "dumbbell", "escalator",
        "fountain", "graveyard", "hurricane", "inferno", "jawbone", "keyhole", "lollipop",
        "microscope", "nightmare", "outlaw", "pretzel", "quicksilver", "riddle", "spaghetti",
        "thunderstorm", "underwear", "venom", "whirlpool", "xerography", "yardstick"
    };

    public static ArrayList<String> getWordList() {
        return new ArrayList<>(Arrays.asList(WORDS));
    }

    public static String randomWord(List<String> from, List<String> exclude) {
        ArrayList<String> pool = new ArrayList<>(from);
        pool.removeAll(exclude);
        if (pool.isEmpty()) return from.get((int) (Math.random() * from.size()));
        return pool.get((int) (Math.random() * pool.size()));
    }
}
