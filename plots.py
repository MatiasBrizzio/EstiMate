import matplotlib.pyplot as plt
import numpy as np

# Read the ranking values from files
with open('./result/defaultrankingNumber.out', 'r') as exact_file:
    exact_ranking = [int(line.strip()) for line in exact_file]

with open('./result/defaultauto-rankingNumber.out', 'r') as approximate_file:
    approximate_ranking = [int(line.strip()) for line in approximate_file]

# Calculate the factor k for each pair of values
factors = [exact / approx for exact, approx in zip(exact_ranking, approximate_ranking)]

# Correlation coefficient (replace with your actual value)
# k=6
# correlation_coefficient = 0.9999809415436086
# factor = 6
# k=7
# correlation_coefficient = 0.9999996036099866
# factor = 7
# k=8
correlation_coefficient = 0.9999999258248851
# factor = 8

# Create a scatter plot with different colors for each ranking
plt.figure(figsize=(8, 6))
plt.scatter(exact_ranking, approximate_ranking, c=factors, cmap='viridis', marker='o', edgecolors='black', s=80)
plt.plot([min(exact_ranking), max(exact_ranking)], [min(approximate_ranking), max(approximate_ranking)], color='red', linestyle='--', label='Perfect Correlation Line')
plt.xlabel('Exact Ranking')
plt.ylabel('Approximate Ranking')
plt.title(f'Ranking Correlation (Pearson Correlation Coefficient: {correlation_coefficient:.4f})')
plt.colorbar(label='Approximate Factor k')
plt.legend()
plt.grid(True)
plt.show()

# Print the approximate factors k
for i, factor in enumerate(factors):
    print(f'Approximate Factor k for position {i + 1}: {factor:.4f}')

