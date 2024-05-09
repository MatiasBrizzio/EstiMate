package main;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CorrelationTest {
    public static double[] convertToDoubleArray(List<String> stringArray) {
        double[] doubleArray = new double[stringArray.size()];
        for (int i = 0; i < stringArray.size(); i++) {
            doubleArray[i] = Double.parseDouble(stringArray.get(i)); // Convert string to double
        }
        return doubleArray;
    }

    public static double calculatePearsonCorrelation(double[] xArray, double[] yArray) {
        if (xArray.length != yArray.length) {
            throw new IllegalArgumentException("Arrays must have the same length");
        }

        PearsonsCorrelation correlation = new PearsonsCorrelation();
        return correlation.correlation(xArray, yArray);
    }

    @Test
    public void Test1() throws IOException, InterruptedException {
        // Replace these arrays with your actual BigInteger ranking values
        String exact_ranking = "./result/defaultrankingNumber.out";
        String approximate_ranking = "./result/defaultauto-rankingNumber.out";
        List<String> exactRanking = new ArrayList<String>();
        List<String> approximateRanking = new ArrayList<String>();


        BufferedReader reader;
        reader = new BufferedReader(new FileReader(exact_ranking));
        String line = reader.readLine();
        while (line != null) {
            exactRanking.add(line);
            line = reader.readLine();
        }
        reader.close();
        line = "";
        reader = new BufferedReader(new FileReader(approximate_ranking));
        line = reader.readLine();
        int i = 0;
        while (line != null) {
            approximateRanking.add(line);
            line = reader.readLine();
        }
        reader.close();

        // Convert string arrays to double arrays
        double[] exactRankingDouble = convertToDoubleArray(exactRanking);
        double[] approximateRankingDouble = convertToDoubleArray(approximateRanking);

        double correlation = calculatePearsonCorrelation(exactRankingDouble, approximateRankingDouble);
        System.out.println("Pearson Correlation Coefficient: " + correlation);


    }
}
