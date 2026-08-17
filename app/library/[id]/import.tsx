import * as DocumentPicker from 'expo-document-picker';
import * as ImagePicker from 'expo-image-picker';
import { router, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useLibraries } from '../../../contexts/LibraryContext';
import {
  fetchLatestImportBatch,
  type ImportBatchResult,
  type ImportFileResult,
  uploadImportFiles,
} from '../../../services/importApi';
import { ui } from '../../../constants/ui';
import { useDialog } from '../../../components/AppDialog';

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
    case 'CONFIRMED':
      return file.errorMessage
        ? `题目已确认入库；${file.errorMessage}`
        : '题目已确认入库，临时原文件已删除';
    case 'DISCARDED':
      return file.errorMessage
        ? `草稿已不入库；${file.errorMessage}`
        : '草稿已不入库，临时原文件已删除';
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
  const confirmedCount = files.filter((file) => file.status === 'CONFIRMED').length;
  const discardedCount = files.filter((file) => file.status === 'DISCARDED').length;
  const failedCount = files.filter((file) =>
    ['RECOGNITION_FAILED', 'STRUCTURING_FAILED', 'UPLOAD_FAILED'].includes(file.status)
  ).length;
  return `共 ${files.length} 个文件：处理中 ${processingCount}，待确认 ${readyCount}，已确认 ${confirmedCount}，不入库 ${discardedCount}，失败 ${failedCount}`;
}

export default function ImportQuestionsScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { libraries } = useLibraries();
  const { showDialog } = useDialog();
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
      showDialog({ title: '需要相机权限', message: '请允许相机权限后再拍照导入。', tone: 'warning' });
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
      showDialog({ title: '需要相册权限', message: '请允许访问相册后再选择题目图片。', tone: 'warning' });
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
      showDialog({ title: '暂不支持该文件', message: '请选择 .docx 格式的 Word 文件。', tone: 'warning' });
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
      showDialog({ title: '上传成功', message: '后台已开始处理。你可以返回学习库，稍后再回来查看进度。', tone: 'success' });
    } catch (error) {
      const message = error instanceof Error ? error.message : '文件上传失败，请稍后重试';
      showDialog({ title: '上传失败', message, tone: 'danger' });
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
      <Text style={styles.libraryText}>目标学习库 · {library.name}</Text>
      <Text style={styles.tipText}>资料上传后会在后台继续处理，你可离开此页，稍后再回来查看进度。</Text>

      <Text style={styles.sectionTitle}>选择导入方式</Text>
      <View style={styles.optionGrid}>
        <Pressable style={styles.optionCard} onPress={captureImage}>
          <View style={[styles.optionIcon, styles.cameraIcon]}><Text style={styles.optionIconText}>◉</Text></View>
          <Text style={styles.optionTitle}>拍照导入</Text>
          <Text style={styles.optionDescription}>使用相机拍题</Text>
        </Pressable>
        <Pressable style={styles.optionCard} onPress={selectImagesFromLibrary}>
          <View style={[styles.optionIcon, styles.galleryIcon]}><Text style={styles.optionIconText}>▧</Text></View>
          <Text style={styles.optionTitle}>从相册选择</Text>
          <Text style={styles.optionDescription}>一次多选图片</Text>
        </Pressable>
        <Pressable style={styles.optionCard} onPress={selectWordDocument}>
          <View style={[styles.optionIcon, styles.wordIcon]}><Text style={styles.optionIconText}>W</Text></View>
          <Text style={styles.optionTitle}>选择 Word</Text>
          <Text style={styles.optionDescription}>导入 .docx</Text>
        </Pressable>
      </View>

      {selectedFiles.length > 0 && (
        <View>
          <Text style={styles.sectionTitle}>待上传文件（{selectedFiles.length}）</Text>
          {selectedFiles.map((file) => (
            <View key={file.uri} style={styles.pendingFileRow}><Text style={styles.pendingFileDot}>•</Text><Text style={styles.fileName}>{file.displayName}</Text></View>
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
          <View style={styles.batchSummaryCard}><Text style={styles.progressText}>{getBatchSummary(latestBatch.files)}</Text></View>
          {latestBatch.files.some((file) =>
            ['WAITING_CONFIRMATION', 'CONFIRMED', 'DISCARDED'].includes(file.status)
          ) && (
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
              <View style={styles.resultTopRow}><Text numberOfLines={2} style={styles.fileName}>{file.originalFileName}</Text><Text style={styles.statusPill}>{getImportStatusText(file)}</Text></View>
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
  container: { backgroundColor: ui.colors.background, flexGrow: 1, padding: 20, paddingBottom: 48, paddingTop: 58 },
  backText: { color: ui.colors.primary, fontSize: 16, fontWeight: '700' },
  title: { color: ui.colors.text, fontSize: 30, fontWeight: '800', marginTop: 26 },
  libraryText: { color: ui.colors.primary, fontSize: 14, fontWeight: '800', marginTop: 12 },
  tipText: { color: ui.colors.mutedText, fontSize: 14, lineHeight: 21, marginTop: 8 },
  sectionTitle: { color: ui.colors.text, fontSize: 19, fontWeight: '800', marginTop: 30 },
  optionGrid: { flexDirection: 'row', gap: 10, marginTop: 12 },
  optionCard: { alignItems: 'center', backgroundColor: ui.colors.surface, borderColor: ui.colors.border, borderRadius: ui.radius.card, borderWidth: 1, flex: 1, minHeight: 150, paddingHorizontal: 8, paddingVertical: 15, ...ui.subtleShadow },
  optionIcon: { alignItems: 'center', borderRadius: 14, height: 48, justifyContent: 'center', width: 48 },
  cameraIcon: { backgroundColor: ui.colors.primarySoft },
  galleryIcon: { backgroundColor: '#F1ECFF' },
  wordIcon: { backgroundColor: '#E9F6FF' },
  optionIconText: { color: ui.colors.primary, fontSize: 20, fontWeight: '800' },
  optionTitle: { color: ui.colors.text, fontSize: 13, fontWeight: '800', marginTop: 11, textAlign: 'center' },
  optionDescription: { color: ui.colors.mutedText, fontSize: 11, lineHeight: 16, marginTop: 5, textAlign: 'center' },
  pendingFileRow: { alignItems: 'center', backgroundColor: ui.colors.surface, borderRadius: 12, flexDirection: 'row', marginTop: 8, padding: 12, ...ui.subtleShadow },
  pendingFileDot: { color: ui.colors.primary, fontSize: 20, marginRight: 8 },
  fileName: { color: ui.colors.text, flex: 1, fontSize: 13, lineHeight: 19 },
  batchSummaryCard: { backgroundColor: ui.colors.primarySoft, borderRadius: 12, marginTop: 10, padding: 12 },
  resultCard: { backgroundColor: ui.colors.surface, borderColor: ui.colors.border, borderRadius: 14, borderWidth: 1, marginTop: 10, padding: 13 },
  resultTopRow: { alignItems: 'flex-start', flexDirection: 'row', gap: 8 },
  statusPill: { backgroundColor: ui.colors.primarySoft, borderRadius: 8, color: ui.colors.primary, flexShrink: 1, fontSize: 11, fontWeight: '700', paddingHorizontal: 7, paddingVertical: 4 },
  structureButton: { alignItems: 'center', borderColor: '#B7CDFC', borderRadius: 10, borderWidth: 1, marginTop: 12, paddingVertical: 10 },
  structureButtonText: { color: ui.colors.primary, fontSize: 13, fontWeight: '800' },
  batchDraftButton: { alignItems: 'center', backgroundColor: ui.colors.primary, borderRadius: ui.radius.button, marginTop: 12, paddingVertical: 14, ...ui.shadow },
  batchDraftButtonText: { color: '#FFFFFF', fontSize: 14, fontWeight: '800' },
  uploadButton: { alignItems: 'center', backgroundColor: ui.colors.primary, borderRadius: ui.radius.button, marginTop: 16, paddingVertical: 15, ...ui.shadow },
  uploadButtonDisabled: { backgroundColor: ui.colors.disabled },
  uploadButtonText: { color: '#FFFFFF', fontSize: 15, fontWeight: '800' },
  progressText: { color: ui.colors.mutedText, fontSize: 13, lineHeight: 20 },
  errorText: { color: ui.colors.danger, fontSize: 14, lineHeight: 21, marginTop: 10 },
  emptyText: { color: ui.colors.mutedText, flex: 1, padding: 20, paddingTop: 64 },
});
