import pandas as pd
import numpy as np
from sklearn.metrics import classification_report, confusion_matrix, accuracy_score, precision_score, recall_score, f1_score
import matplotlib.pyplot as plt 
import seaborn as sns

# Read the CSV file with the analysis results
results_df = pd.read_csv('analysis_results_20250522_143244.csv')

# Extract ground truth and predicted labels
y_true = results_df['GROUND_TRUTH_LABEL']
y_pred = results_df['PREDICTED_LABEL']

# Calculate metrics
accuracy = accuracy_score(y_true, y_pred)
precision = precision_score(y_true, y_pred, pos_label='MALWARE') 
recall = recall_score(y_true, y_pred, pos_label='MALWARE')
f1 = f1_score(y_true, y_pred, pos_label='MALWARE')

# Print metrics
print("Performance Metrics:")
print(f"Accuracy: {accuracy:.4f}")
print(f"Precision: {precision:.4f}") 
print(f"Recall: {recall:.4f}")
print(f"F1 Score: {f1:.4f}")

# Generate classification report
print("\nClassification Report:")
print(classification_report(y_true, y_pred))

# Create confusion matrix visualization
cm = confusion_matrix(y_true, y_pred, labels=['MALWARE', 'SAFE'])
plt.figure(figsize=(8,6))
sns.heatmap(cm, annot=True, fmt='d', cmap='Blues',
            xticklabels=['MALWARE', 'SAFE'],
            yticklabels=['MALWARE', 'SAFE'])
plt.title('Confusion Matrix')
plt.xlabel('Predicted Label')
plt.ylabel('True Label')
plt.savefig('confusion_matrix.png')
plt.close()

# Find misclassified samples
misclassified = results_df[results_df['GROUND_TRUTH_LABEL'] != results_df['PREDICTED_LABEL']]
print(f"\nNumber of misclassified samples: {len(misclassified)}")

# Save misclassified samples to CSV
misclassified.to_csv('misclassified_samples.csv', index=False)

# Generate summary report
with open('analysis_summary.txt', 'w') as f:
    f.write("APK Malware Detection Analysis Summary\n")
    f.write("====================================\n\n")
    f.write(f"Total samples analyzed: {len(results_df)}\n")
    f.write(f"Class distribution: {dict(y_true.value_counts())}\n\n")
    f.write("Performance Metrics:\n")
    f.write(f"Accuracy: {accuracy:.4f}\n")
    f.write(f"Precision: {precision:.4f}\n")
    f.write(f"Recall: {recall:.4f}\n")
    f.write(f"F1 Score: {f1:.4f}\n\n")
    f.write("Classification Report:\n")
    f.write(classification_report(y_true, y_pred))
    f.write(f"\nNumber of misclassified samples: {len(misclassified)}")