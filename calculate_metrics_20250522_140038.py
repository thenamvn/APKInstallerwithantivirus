import pandas as pd
import numpy as np
from sklearn.metrics import classification_report, confusion_matrix, accuracy_score, precision_score, recall_score, f1_score
import matplotlib.pyplot as plt
import seaborn as sns

# Load data
dataset_df = pd.read_csv("/storage/emulated/0/Android/data/com.alphawolf.apkinstallerwithantivirus/files/test_results/dataset_info_20250522_140038.csv")
results_df = pd.read_csv("/storage/emulated/0/Android/data/com.alphawolf.apkinstallerwithantivirus/files/test_results/analysis_results_20250522_140038.csv")

# Calculate metrics
y_true = results_df['GROUND_TRUTH_LABEL']
y_pred = results_df['PREDICTED_LABEL']

# Basic metrics
accuracy = accuracy_score(y_true, y_pred)

# Chỉ xử lý binary classification (SAFE vs MALWARE)
precision = precision_score(y_true, y_pred, pos_label='MALWARE')
recall = recall_score(y_true, y_pred, pos_label='MALWARE')
f1 = f1_score(y_true, y_pred, pos_label='MALWARE')

# Generate report
print(f"Accuracy: {accuracy:.4f}")
print(f"Precision: {precision:.4f}")
print(f"Recall: {recall:.4f}")
print(f"F1 Score: {f1:.4f}")

print("\nClassification Report:")
print(classification_report(y_true, y_pred))

# Create confusion matrix
cm = confusion_matrix(y_true, y_pred)
plt.figure(figsize=(10, 8))
sns.heatmap(cm, annot=True, fmt='d', cmap='Blues',
            xticklabels=['SAFE', 'MALWARE'],
            yticklabels=['SAFE', 'MALWARE'])
plt.title('Confusion Matrix')
plt.xlabel('Predicted')
plt.ylabel('Actual')
plt.tight_layout()
plt.savefig("/storage/emulated/0/Android/data/com.alphawolf.apkinstallerwithantivirus/files/test_results/confusion_matrix.png")

# Export misclassified samples
errors_df = results_df[results_df['GROUND_TRUTH_LABEL'] != results_df['PREDICTED_LABEL']]
errors_df.to_csv(f"{outputDir}/misclassified_apks.csv", index=False)
print(f"\nMisclassified samples: {len(errors_df)}/{len(results_df)} ({len(errors_df)/len(results_df)*100:.2f}%)")

# Summary file
with open("/storage/emulated/0/Android/data/com.alphawolf.apkinstallerwithantivirus/files/test_results/metrics_summary.txt", "w") as f:
    f.write(f"APK Malware Detection Evaluation\n")
    f.write(f"============================\n\n")
    f.write(f"Dataset: {len(results_df)} APK files\n")
    f.write(f"Distribution: {dict(y_true.value_counts())}\n\n")
    f.write(f"Accuracy: {accuracy:.4f}\n")
    f.write(f"Precision: {precision:.4f}\n")
    f.write(f"Recall: {recall:.4f}\n")
    f.write(f"F1 Score: {f1:.4f}\n\n")
    f.write("Classification Report:\n")
    f.write(classification_report(y_true, y_pred))

print(f"\nResults saved to: /storage/emulated/0/Android/data/com.alphawolf.apkinstallerwithantivirus/files/test_results")