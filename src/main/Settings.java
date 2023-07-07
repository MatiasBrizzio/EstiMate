package main;

import java.util.Random;

public class Settings {
    public static Random RANDOM_GENERATOR = new Random(System.currentTimeMillis());
    public static int PARSING_TIMEOUT = 60;
    public static int MC_TIMEOUT = 180;
    public static int SAT_TIMEOUT = 60;
    public static String STRIX_PATH = "";
    public static int MC_BOUND = 10;
}

