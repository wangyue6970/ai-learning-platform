import * as DocumentPicker from 'expo-document-picker';
import * as ImagePicker from 'expo-image-picker';
import { router, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useLibraries } from '../../../contexts/LibraryContext';
import {
  fetchLatestImportBatch,
  type ImportBatchResult,
  type ImportFileResult,
  uploadImportFiles,
} from '../../../services/importApi';

type PendingImportFile = {
  uri: string;
  displayName: string;
};

function isProcessingFile(file: ImportFileResult) {
  return ['WAITING_RECOGNITION', 'RECOGNIZING', 'WAITING_STRUCTURING', 'STRUCTURING'].includes(file.status);
}

function getImportStatusText(file: ImportFileResult) {
  switch (file.status) {
    case 'WAITING_RECOGNITION':
      return '已上传，等待后台处理';
    case 'RECOGNIZING':
      return '正在识别文件';
    case 'WAITING_STRUCTURING':
      return '已识别，等待生成题目';
    case 'STRUCTURING':
      return '正在生成题目草稿';
    case 'WAITING_CONFIRMATION':
      return '题目草稿已生成，等待确认';
    case 'RECOGNITION_FAILED':
      return `识别失败：${file.errorMessage || '请稍后重试'}`;
    case 'STRUCTURING_FAILED':
      return `生成题目失败：${file.errorMessage || '请稍后重试'}`;
    case 'UPLOAD_FAILED':
      return `上传失败：${file.errorMessage || '请稍后重试'}`;
  }
}

function getBatchSummary(files: ImportFileResult[]) {
  const processingCount = files.filter(isProcessingFile).length;
  const readyCount = files.filter((file) => file.status === 'WAITING_CONFIRMATION').length;
  const failedCount = files.length - processingCount - readyCount;
  return `共 ${files.length} 个文件：处理中 ${processingCount}，待确认 ${readyCount}，失败 ${failedCount}`;
}

export default function ImportQuestionsScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { libraries } = useLibraries();
  const library = libraries.find((item) => item.id === id);
  const [selectedFiles, setSelectedFiles] = useState<PendingImportFile[]>([]);
  const [uploading, setUploading] = useState(false);
  const [latestBatch, setLatestBatch] = useState<ImportBatchResult | null>(null);
  const [isLoadingBatch, setIsLoadingBatch] = useState(true);
  const [batchLoadError, setBatchLoadError] = useState<string | null>(null);

  const loadLatestBatch = useCallback(async () => {
    if (!id) {
      return;
    }

    try {
      const batch = await fetchLatestImportBatch(id);
      setLatestBatch(batch);
      setBatchLoadError(null);
    } catch (error) {
      setBatchLoadError(error instanceof Error ? error.message : '读取导入进度失败，请稍后重试');
    } finally {
      setIsLoadingBatch(false);
    }
  }, [id]);

  useFocusEffect(
    useCallback(() => {
      void loadLatestBatch();
    }, [loadLatestBatch])
  );

  useEffect(() => {
    if (!latestBatch?.files.some(isProcessingFile)) {
      return;
    }

    const timer = setInterval(() => void loadLatestBatch(), 2000);
    return () => clearInterval(timer);
  }, [latestBatch, loadLatestBatch]);

  function addSelectedFiles(files: PendingImportFile[]) {
    setSelectedFiles((currentFiles) => {
      const existingUris = new Set(currentFiles.map((file) => file.uri));
      return [...currentFiles, ...files.filter((file) => !existingUris.has(file.uri))];
    });
  }

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

    const image = result.assets[0];
    addSelectedFiles([{ uri: image.uri, displayName: image.fileName || '拍摄的题目图片' }]);
  }

  async function selectImagesFromLibrary() {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      Alert.alert('需要相册权限', '请允许访问相册后再选择题目图片。');
      return;
    }

    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      allowsMultipleSelection: true,
      selectionLimit: 0,
      quality: 0.8,
    });
    if (result.canceled || result.assets.length === 0) {
      return;
    }

    addSelectedFiles(result.assets.map((image) => ({
      uri: image.uri,
      displayName: image.fileName || '相册题目图片',
    })));
  }

  async function selectWordDocument() {
    const result = await DocumentPicker.getDocumentAsync({
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      copyToCacheDirectory: true,
      multiple: false,
    });
    if (result.canceled || !result.assets[0]) {
      return;
    }

    const wordFile = result.assets[0];
    if (!wordFile.name.toLowerCase().endsWith('.docx')) {
      Alert.alert('暂不支持该文件', '请选择 .docx 格式的 Word 文件。');
      return;
    }

    addSelectedFiles([{ uri: wordFile.uri, displayName: wordFile.name }]);
  }

  async function uploadSelectedFiles() {
    if (selectedFiles.length === 0 || uploading) {
      return;
    }

    const filesForThisUpload = selectedFiles;
    setUploading(true);
    try {
      const batch = await uploadImportFiles(id, filesForThisUpload);
      setLatestBatch(batch);
      setSelectedFiles((currentFiles) =>
        currentFiles.filter((_, index) => batch.files[index]?.status === 'UPLOAD_FAILED')
      );
      Alert.alert('上传成功', '后台已开始处理。你可以返回学习库，稍后再回来查看进度。');
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
      <Text style={styles.tipText}>上传完成后，后端会继续处理；你可以离开此页面，之后再回来查看。</Text>

      <Text style={styles.sectionTitle}>选择导入方式</Text>
      <Pressable style={styles.optionCard} onPress={captureImage}>
        <Text style={styles.optionTitle}>拍照导入</Text>
        <Text style={styles.optionDescription}>使用手机相机拍摄题目图片</Text>
      </Pressable>
      <Pressable style={styles.optionCard} onPress={selectImagesFromLibrary}>
        <Text style={styles.optionTitle}>从相册选择</Text>
        <Text style={styles.optionDescription}>一次选择任意多张题目图片</Text>
      </Pressable>
      <Pressable style={styles.optionCard} onPress={selectWordDocument}>
        <Text style={styles.optionTitle}>选择 Word</Text>
        <Text style={styles.optionDescription}>选择包含题目的 Word 文件</Text>
      </Pressable>

      {selectedFiles.length > 0 && (
        <View>
          <Text style={styles.sectionTitle}>待上传文件（{selectedFiles.length}）</Text>
          {selectedFiles.map((file) => (
            <Text key={file.uri} style={styles.fileName}>{file.displayName}</Text>
          ))}
          <Pressable
            disabled={uploading}
            style={[styles.uploadButton, uploading && styles.uploadButtonDisabled]}
            onPress={() => void uploadSelectedFiles()}>
            <Text style={styles.uploadButtonText}>
              {uploading ? '正在上传…' : `上传 ${selectedFiles.length} 个文件并开始处理`}
            </Text>
          </Pressable>
        </View>
      )}

      <Text style={styles.sectionTitle}>最近导入批次</Text>
      {isLoadingBatch && !latestBatch && <Text style={styles.progressText}>正在读取导入进度…</Text>}
      {batchLoadError && <Text style={styles.errorText}>{batchLoadError}</Text>}
      {!isLoadingBatch && !batchLoadError && !latestBatch && (
        <Text style={styles.progressText}>还没有导入过文件。</Text>
      )}
      {latestBatch && (
        <View>
          <Text style={styles.progressText}>{getBatchSummary(latestBatch.files)}</Text>
          {latestBatch.files.some((file) => file.status === 'WAITING_CONFIRMATION') && (
            <Pressable
              style={styles.batchDraftButton}
              onPress={() => router.push({
                pathname: '/library/[id]/drafts',
                params: { id, importBatchId: String(latestBatch.id) },
              })}>
              <Text style={styles.batchDraftButtonText}>集中查看本批次草稿</Text>
            </Pressable>
          )}
          {latestBatch.files.map((file) => (
            <View key={file.id} style={styles.resultCard}>
              <Text style={styles.fileName}>{file.originalFileName}：{getImportStatusText(file)}</Text>
              {file.status === 'WAITING_CONFIRMATION' && (
                <Pressable
                  style={styles.structureButton}
                  onPress={() => router.push({
                    pathname: '/library/[id]/drafts/[importFileId]',
                    params: { id, importFileId: String(file.id) },
                  })}>
                  <Text style={styles.structureButtonText}>查看题目草稿</Text>
                </Pressable>
              )}
            </View>
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
  tipText: { color: '#64748B', fontSize: 14, lineHeight: 21, marginTop: 10 },
  sectionTitle: { color: '#0F172A', fontSize: 18, fontWeight: '700', marginTop: 32 },
  optionCard: { backgroundColor: '#FFFFFF', borderColor: '#E2E8F0', borderRadius: 12, borderWidth: 1, marginTop: 12, padding: 18 },
  optionTitle: { color: '#0F172A', fontSize: 17, fontWeight: '700' },
  optionDescription: { color: '#64748B', fontSize: 14, marginTop: 6 },
  fileName: { color: '#334155', fontSize: 14, lineHeight: 21 },
  resultCard: { backgroundColor: '#FFFFFF', borderRadius: 8, marginTop: 8, padding: 12 },
  structureButton: { alignItems: 'center', borderColor: '#2563EB', borderRadius: 8, borderWidth: 1, marginTop: 10, paddingVertical: 10 },
  structureButtonText: { color: '#2563EB', fontSize: 14, fontWeight: '700' },
  batchDraftButton: { alignItems: 'center', backgroundColor: '#2563EB', borderRadius: 8, marginTop: 12, paddingVertical: 12 },
  batchDraftButtonText: { color: '#FFFFFF', fontSize: 14, fontWeight: '700' },
  uploadButton: { alignItems: 'center', backgroundColor: '#2563EB', borderRadius: 10, marginTop: 16, paddingVertical: 14 },
  uploadButtonDisabled: { backgroundColor: '#93C5FD' },
  uploadButtonText: { color: '#FFFFFF', fontSize: 15, fontWeight: '700' },
  progressText: { color: '#64748B', fontSize: 14, lineHeight: 21, marginTop: 10 },
  errorText: { color: '#DC2626', fontSize: 14, lineHeight: 21, marginTop: 10 },
  emptyText: { flex: 1, padding: 20, paddingTop: 64 },
});
