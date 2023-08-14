import matplotlib.pyplot as plt
import numpy as np

# Read the ranking values from files
with open('./result/defaultrankingNumber.out', 'r') as exact_file:
    exact_ranking = [int(line.strip()) for line in exact_file]

with open('./result/defaultauto-rankingNumber.out', 'r') as approximate_file:
    approximate_ranking = [int(line.strip()) for line in approximate_file]

# Correlation coefficient (replace with your actual value)
correlation_coefficient = 0.9999809415436086

# Create a scatter plot
plt.figure(figsize=(8, 6))
plt.scatter(exact_ranking, approximate_ranking, color='blue', label='Data Points')
plt.plot([min(exact_ranking), max(exact_ranking)], [min(approximate_ranking), max(approximate_ranking)], color='red', linestyle='--', label='Perfect Correlation Line')
plt.xlabel('Exact Ranking')
plt.ylabel('Approximate Ranking')
plt.title(f'Ranking Correlation (Pearson Correlation Coefficient: {correlation_coefficient:.4f})')
plt.legend()
plt.grid(True)
plt.show()
