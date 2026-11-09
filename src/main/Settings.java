package main;

import java.util.Random;

public class Settings {
    public static Random RANDOM_GENERATOR = new Random(System.currentTimeMillis());
    public static int PARSING_TIMEOUT = 60;
    public static int MC_TIMEOUT = 180;
    public static int SAT_TIMEOUT = 60;
    /** "aalta" (default) or "black". aalta shares a work file in its current directory. */
    public static String SAT_SOLVER = "aalta";
    /** Solver binary; empty means the bundled aalta, or black from the PATH. */
    public static String SAT_BIN = "";
    public static int MC_BOUND = 10;
}

