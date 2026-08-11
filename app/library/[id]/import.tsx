import { router, useLocalSearchParams } from 'expo-router';
import * as ImagePicker from 'expo-image-picker';
import { useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useLibraries } from '../../../contexts/LibraryContext';
import { ImportFileResult, uploadImportFiles } from '../../../services/importApi';

const importOptions = [
  { title: '拍照导入', description: '使用手机相机拍摄题目图片' },
  { title: '从相册选择', description: '一次选择多张题目图片' },
  { title: '选择 Word', description: '选择包含题目的 Word 文件' },
];

export default function ImportQuestionsScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { libraries } = useLibraries();
  const library = libraries.find((item) => item.id === id);
  const [selectedImages, setSelectedImages] = useState<ImagePicker.ImagePickerAsset[]>([]);
  const [uploading, setUploading] = useState(false);
  const [uploadResults, setUploadResults] = useState<ImportFileResult[]>([]);

  async function captureImage() {
    const permission = await ImagePicker.requestCameraPermissionsAsync();

    if (!permission.granted) {
      Alert.alert('需要相机权限', '请允许相机权限后再拍照导入。');
      return;
    }

    const result = await ImagePicker.launchCameraAsync({ mediaTypes: ['images'], quality: 0.8 });

    if (result.canceled || !result.assets[0]) {
      return;
    }

    setSelectedImages((currentImages) => [...currentImages, result.assets[0]]);
  }

  async function uploadSelectedImages() {
    if (selectedImages.length === 0 || uploading) {
      return;
    }

    setUploading(true);
    setUploadResults([]);

    try {
      const result = await uploadImportFiles(id, selectedImages);
      setUploadResults(result.files);
      setSelectedImages((currentImages) =>
        currentImages.filter((_, index) => result.files[index]?.status !== 'WAITING_RECOGNITION')
      );
    } catch (error) {
      const message = error instanceof Error ? error.message : '文件上传失败，请稍后重试';
      Alert.alert('上传失败', message);
    } finally {
      setUploading(false);
    }
  }

  if (!library) {
    return <Text style={styles.emptyText}>学习库不存在。</Text>;
  }

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Pressable onPress={() => router.back()}>
        <Text style={styles.backText}>返回</Text>
      </Pressable>
      <Text style={styles.title}>导入题目</Text>
      <Text style={styles.libraryText}>保存到：{library.name}</Text>
      <Text style={styles.sectionTitle}>选择导入方式</Text>
      <Pressable style={styles.optionCard} onPress={captureImage}>
        <Text style={styles.optionTitle}>拍照导入</Text>
        <Text style={styles.optionDescription}>使用手机相机拍摄题目图片</Text>
      </Pressable>
      {importOptions.slice(1).map((option) => (
        <View key={option.title} style={styles.optionCard}>
          <Text style={styles.optionTitle}>{option.title}</Text>
          <Text style={styles.optionDescription}>{option.description}</Text>
        </View>
      ))}
      {selectedImages.length > 0 && (
        <View>
          <Text style={styles.sectionTitle}>待上传文件（{selectedImages.length}）</Text>
          {selectedImages.map((image) => (
            <Text key={image.uri} style={styles.fileName}>
              {image.fileName || '拍摄的题目图片'}
            </Text>
          ))}
          <Pressable
            style={[styles.uploadButton, uploading && styles.uploadButtonDisabled]}
            onPress={uploadSelectedImages}
          >
            <Text style={styles.uploadButtonText}>
              {uploading ? '正在上传…' : `上传 ${selectedImages.length} 个文件`}
            </Text>
          </Pressable>
        </View>
      )}
      {uploadResults.length > 0 && (
        <View>
          <Text style={styles.sectionTitle}>上传结果</Text>
          {uploadResults.map((file) => (
            <Text key={file.id} style={styles.fileName}>
              {file.originalFileName}：
              {file.status === 'WAITING_RECOGNITION' ? '已上传，等待识别' : `上传失败：${file.errorMessage}`}
            </Text>
          ))}
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { backgroundColor: '#F8FAFC', flexGrow: 1, padding: 20, paddingBottom: 40, paddingTop: 64 },
  backText: { color: '#2563EB', fontSize: 16 },
  title: { color: '#0F172A', fontSize: 28, fontWeight: '700', marginTop: 24 },
  libraryText: { color: '#475569', fontSize: 16, marginTop: 10 },
  sectionTitle: { color: '#0F172A', fontSize: 18, fontWeight: '700', marginTop: 32 },
  optionCard: { backgroundColor: '#FFFFFF', borderColor: '#E2E8F0', borderRadius: 12, borderWidth: 1, marginTop: 12, padding: 18 },
  optionTitle: { color: '#0F172A', fontSize: 17, fontWeight: '700' },
  optionDescription: { color: '#64748B', fontSize: 14, marginTop: 6 },
  fileName: { backgroundColor: '#FFFFFF', borderRadius: 8, color: '#334155', marginTop: 8, padding: 12 },
  uploadButton: { alignItems: 'center', backgroundColor: '#2563EB', borderRadius: 10, marginTop: 16, paddingVertical: 14 },
  uploadButtonDisabled: { backgroundColor: '#93C5FD' },
  uploadButtonText: { color: '#FFFFFF', fontSize: 15, fontWeight: '700' },
  emptyText: { flex: 1, padding: 20, paddingTop: 64 },
});
