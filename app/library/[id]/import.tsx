import * as DocumentPicker from 'expo-document-picker';
import * as ImagePicker from 'expo-image-picker';
import { router, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useLibraries } from '../../../contexts/LibraryContext';
import {
  fetchLatestImportBatch,
  reparseWordImportFile,
  retryFailedImportFile,
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

function hasChunkProgress(file: ImportFileResult) {
  return (file.totalChunkCount ?? 0) > 0;
}

function getChunkProgressText(file: ImportFileResult) {
  const totalChunks = file.totalChunkCount ?? 0;
  const completedChunks = file.completedChunkCount ?? 0;
  const generatedDrafts = file.generatedDraftCount ?? 0;
  const estimatedQuestions = file.estimatedQuestionCount ?? 0;
  const batchText = file.status === 'STRUCTURING' && completedChunks < totalChunks
    ? `正在处理第 ${completedChunks + 1} / ${totalChunks} 批`
    : `已完成 ${completedChunks} / ${totalChunks} 批`;
  const estimateText = estimatedQuestions > 0 ? ` · 原文约 ${estimatedQuestions} 题` : '';
  return `${batchText} · 已生成 ${generatedDrafts} 道草稿${estimateText}`;
}

function getChunkProgressPercentage(file: ImportFileResult) {
  const totalChunks = file.totalChunkCount ?? 0;
  if (totalChunks === 0) {
    return 0;
  }
  return Math.min(100, Math.round(((file.completedChunkCount ?? 0) / totalChunks) * 100));
}

function getBatchSummary(files: ImportFileResult[]) {
  const processingCount = files.filter(isProcessingFile).length;
  const readyCount = files.filter((file) => file.status === 'WAITING_CONFIRMATION').length;
  const confirmedCount = files.filter((file) => file.status === 'CONFIRMED').length;
  const discardedCount = files.filter((file) => file.status === 'DISCARDED').length;
  const failedCount = files.filter((file) =>
    ['RECOGNITION_FAILED', 'STRUCTURING_FAILED', 'UPLOAD_FAILED'].includes(file.status)
  ).length;
  const needsReviewCount = files.reduce((total, file) => total + (file.needsReviewDraftCount || 0), 0);
  return `共 ${files.length} 个文件：处理中 ${processingCount}，待修正 ${needsReviewCount}，待确认 ${readyCount}，已确认 ${confirmedCount}，不入库 ${discardedCount}，失败 ${failedCount}`;
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
  const [retryingFileId, setRetryingFileId] = useState<number | null>(null);

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

  async function retryFailedFile(file: ImportFileResult) {
    if (retryingFileId !== null) {
      return;
    }

    setRetryingFileId(file.id);
    try {
      await retryFailedImportFile(id, file.id);
      await loadLatestBatch();
      showDialog({
        title: '已重新开始处理',
        message: '原文件无需重新上传。后台会从未完成的位置继续处理。',
        tone: 'success',
      });
    } catch (error) {
      showDialog({
        title: '重新处理失败',
        message: error instanceof Error ? error.message : '请稍后重试',
        tone: 'danger',
      });
    } finally {
      setRetryingFileId(null);
    }
  }

  async function reparseWordFile(file: ImportFileResult) {
    if (retryingFileId !== null) {
      return;
    }
    setRetryingFileId(file.id);
    try {
      await reparseWordImportFile(id, file.id);
      await loadLatestBatch();
      showDialog({
        title: '已开始按 Word 原文重新整理',
        message: '旧的未确认草稿已替换；标准题库 Word 会直接保留原题干、选项和答案。',
        tone: 'success',
      });
    } catch (error) {
      showDialog({
        title: '重新整理失败',
        message: error instanceof Error ? error.message : '请稍后重试',
        tone: 'danger',
      });
    } finally {
      setRetryingFileId(null);
    }
  }

  if (!library) {
    return <Text style={styles.emptyText}>学习库不存在。</Text>;
  }

  const generatedDraftCount = latestBatch?.files.reduce(
    (total, file) => total + (file.generatedDraftCount || 0),
    0
  ) || 0;
  const isLatestBatchProcessing = latestBatch?.files.some(isProcessingFile) || false;

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <View style={styles.topBar}>
        <Pressable style={styles.backButton} onPress={() => router.back()}>
          <Text style={styles.backText}>‹</Text>
        </Pressable>
        <Text style={styles.topTitle}>导入题目</Text>
        <View style={styles.topSpacer} />
      </View>
      <View style={styles.pageIntro}>
        <Text style={styles.libraryText}>目标学习库 · {library.name}</Text>
        <Text style={styles.tipText}>可一次选择多张图片或一个 Word 文档，后台会继续处理。</Text>
      </View>

      <Text style={styles.sectionTitle}>选择导入方式</Text>
      <View style={styles.optionGrid}>
        <Pressable style={styles.optionCard} onPress={captureImage}>
          <View style={[styles.optionIcon, styles.cameraIcon]}><Text style={styles.optionIconText}>◉</Text></View>
          <Text style={styles.optionTitle}>拍照导入</Text>
          <Text style={styles.optionDescription}>拍摄题目图片</Text>
        </Pressable>
        <Pressable style={styles.optionCard} onPress={selectImagesFromLibrary}>
          <View style={[styles.optionIcon, styles.galleryIcon]}><Text style={styles.optionIconText}>▧</Text></View>
          <Text style={styles.optionTitle}>从相册选择</Text>
          <Text style={styles.optionDescription}>一次选择多张</Text>
        </Pressable>
        <Pressable style={styles.optionCard} onPress={selectWordDocument}>
          <View style={[styles.optionIcon, styles.wordIcon]}><Text style={styles.optionIconText}>W</Text></View>
          <Text style={styles.optionTitle}>选择 Word</Text>
          <Text style={styles.optionDescription}>选择 .docx 文档</Text>
        </Pressable>
      </View>

      {selectedFiles.length > 0 && (
        <View>
          <View style={styles.fileSectionHeading}>
            <Text style={styles.sectionTitle}>导入文件（{selectedFiles.length}）</Text>
            <Text style={styles.fileSectionHint}>待上传</Text>
          </View>
          {selectedFiles.map((file) => (
            <View key={file.uri} style={styles.pendingFileRow}>
              <View style={styles.fileIcon}><Text style={styles.fileIconText}>{file.displayName.toLowerCase().endsWith('.docx') ? 'W' : '▧'}</Text></View>
              <Text numberOfLines={2} style={styles.fileName}>{file.displayName}</Text>
              <Text style={styles.pendingPill}>待上传</Text>
            </View>
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

      <Text style={styles.sectionTitle}>导入记录</Text>
      {isLoadingBatch && !latestBatch && <Text style={styles.progressText}>正在读取导入进度…</Text>}
      {batchLoadError && <Text style={styles.errorText}>{batchLoadError}</Text>}
      {!isLoadingBatch && !batchLoadError && !latestBatch && (
        <Text style={styles.progressText}>还没有导入过文件。</Text>
      )}
      {latestBatch && (
        <View>
          <View style={styles.batchSummaryCard}>
            <Text style={styles.batchSummaryTitle}>本批次处理进度</Text>
            <Text style={styles.progressText}>{getBatchSummary(latestBatch.files)}</Text>
          </View>
          {generatedDraftCount > 0 && (
            <Pressable
              style={styles.batchDraftButton}
              onPress={() => router.push({
                pathname: '/library/[id]/drafts',
                params: { id, importBatchId: String(latestBatch.id), live: isLatestBatchProcessing ? '1' : '0' },
              })}>
              <Text style={styles.batchDraftButtonText}>
                {isLatestBatchProcessing
                  ? `查看已生成 ${generatedDraftCount} 道草稿`
                  : '集中查看本批次草稿'}
              </Text>
            </Pressable>
          )}
          {!latestBatch.files.some(isProcessingFile) && latestBatch.files.some((file) => (file.needsReviewDraftCount || 0) > 0) && (
            <Pressable
              style={styles.reviewDraftButton}
              onPress={() => router.push({
                pathname: '/library/[id]/drafts',
                params: { id, importBatchId: String(latestBatch.id), filter: 'needs_review' },
              })}>
              <Text style={styles.reviewDraftButtonText}>
                集中修正 {latestBatch.files.reduce((total, file) => total + (file.needsReviewDraftCount || 0), 0)} 道问题题目
              </Text>
            </Pressable>
          )}
          {latestBatch.files.map((file) => (
            <View key={file.id} style={styles.resultCard}>
              <View style={styles.fileIcon}><Text style={styles.fileIconText}>{file.originalFileName.toLowerCase().endsWith('.docx') ? 'W' : '▧'}</Text></View>
              <View style={styles.resultInfo}>
                <View style={styles.resultTopRow}><Text numberOfLines={2} style={styles.fileName}>{file.originalFileName}</Text><Text style={styles.statusPill}>{getImportStatusText(file)}</Text></View>
              {hasChunkProgress(file) && (
                <View style={styles.chunkProgressBox}>
                  <Text style={styles.chunkProgressText}>{getChunkProgressText(file)}</Text>
                  <View style={styles.chunkProgressTrack}>
                    <View style={[styles.chunkProgressFill, { width: `${getChunkProgressPercentage(file)}%` }]} />
                  </View>
                </View>
              )}
              {file.errorMessage && ['WAITING_CONFIRMATION', 'STRUCTURING_FAILED'].includes(file.status) && (
                <Text style={styles.fileErrorText}>{file.errorMessage}</Text>
              )}
              {(file.needsReviewDraftCount || 0) > 0 && (
                <Text style={styles.reviewHint}>有 {file.needsReviewDraftCount} 道题需要补充或修正后才能入库</Text>
              )}
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
              {['RECOGNITION_FAILED', 'STRUCTURING_FAILED'].includes(file.status) && (
                <Pressable
                  disabled={retryingFileId !== null}
                  style={[styles.structureButton, retryingFileId !== null && styles.retryButtonDisabled]}
                  onPress={() => void retryFailedFile(file)}>
                  <Text style={styles.structureButtonText}>
                    {retryingFileId === file.id
                      ? '正在重新开始…'
                      : file.status === 'STRUCTURING_FAILED' ? '按新规则重新生成' : '重新识别文件'}
                  </Text>
                </Pressable>
              )}
              {file.status === 'WAITING_CONFIRMATION'
                && (file.generatedDraftCount || 0) > 0
                && file.needsReviewDraftCount === file.generatedDraftCount && (
                <Pressable
                  disabled={retryingFileId !== null}
                  style={[styles.structureButton, retryingFileId !== null && styles.retryButtonDisabled]}
                  onPress={() => void reparseWordFile(file)}>
                  <Text style={styles.structureButtonText}>
                    {retryingFileId === file.id ? '正在重新整理…' : '按 Word 原文重新整理'}
                  </Text>
                </Pressable>
              )}
              {file.status === 'WAITING_CONFIRMATION'
                && !!file.errorMessage
                && (file.completedChunkCount || 0) < (file.totalChunkCount || 0) && (
                <Pressable
                  disabled={retryingFileId !== null}
                  style={[styles.structureButton, retryingFileId !== null && styles.retryButtonDisabled]}
                  onPress={() => void retryFailedFile(file)}>
                  <Text style={styles.structureButtonText}>
                    {retryingFileId === file.id ? '正在继续处理…' : '继续处理剩余题目'}
                  </Text>
                </Pressable>
              )}
              </View>
            </View>
          ))}
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { backgroundColor: ui.colors.background, flexGrow: 1, paddingHorizontal: 18, paddingBottom: 48, paddingTop: 53 },
  topBar: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  backButton: { alignItems: 'center', height: 32, justifyContent: 'center', width: 32 },
  backText: { color: ui.colors.text, fontSize: 30, fontWeight: '400', lineHeight: 32 },
  topTitle: { color: ui.colors.text, fontSize: 17, fontWeight: '800' },
  topSpacer: { width: 32 },
  pageIntro: { alignItems: 'center', backgroundColor: ui.colors.surface, borderColor: ui.colors.border, borderRadius: 14, borderWidth: 1, flexDirection: 'row', justifyContent: 'space-between', marginTop: 20, paddingHorizontal: 14, paddingVertical: 12, ...ui.subtleShadow },
  libraryText: { color: ui.colors.text, flex: 1, fontSize: 13, fontWeight: '800' },
  tipText: { color: ui.colors.mutedText, flex: 1, fontSize: 11, lineHeight: 16, marginLeft: 12, textAlign: 'right' },
  sectionTitle: { color: ui.colors.text, fontSize: 18, fontWeight: '800', marginTop: 27 },
  optionGrid: { flexDirection: 'row', gap: 9, marginTop: 12 },
  optionCard: { alignItems: 'center', backgroundColor: ui.colors.surface, borderColor: '#EDF0F6', borderRadius: 14, borderWidth: 1, flex: 1, minHeight: 130, paddingHorizontal: 6, paddingVertical: 14, ...ui.subtleShadow },
  optionIcon: { alignItems: 'center', borderRadius: 13, height: 45, justifyContent: 'center', width: 45 },
  cameraIcon: { backgroundColor: ui.colors.primarySoft },
  galleryIcon: { backgroundColor: '#F1ECFF' },
  wordIcon: { backgroundColor: '#E9F6FF' },
  optionIconText: { color: ui.colors.primary, fontSize: 20, fontWeight: '800' },
  optionTitle: { color: ui.colors.text, fontSize: 12, fontWeight: '800', marginTop: 10, textAlign: 'center' },
  optionDescription: { color: ui.colors.mutedText, fontSize: 10, lineHeight: 15, marginTop: 5, textAlign: 'center' },
  fileSectionHeading: { alignItems: 'baseline', flexDirection: 'row', justifyContent: 'space-between' },
  fileSectionHint: { color: ui.colors.primary, fontSize: 11, fontWeight: '800' },
  pendingFileRow: { alignItems: 'center', backgroundColor: ui.colors.surface, borderColor: '#EDF0F6', borderRadius: 12, borderWidth: 1, flexDirection: 'row', marginTop: 8, padding: 11, ...ui.subtleShadow },
  fileIcon: { alignItems: 'center', backgroundColor: ui.colors.primarySoft, borderRadius: 9, height: 32, justifyContent: 'center', marginRight: 9, width: 32 },
  fileIconText: { color: ui.colors.primary, fontSize: 14, fontWeight: '800' },
  fileName: { color: ui.colors.text, flex: 1, fontSize: 12, lineHeight: 18 },
  pendingPill: { backgroundColor: '#FFF1D9', borderRadius: 10, color: '#B46908', fontSize: 10, fontWeight: '800', marginLeft: 8, overflow: 'hidden', paddingHorizontal: 7, paddingVertical: 4 },
  batchSummaryCard: { backgroundColor: ui.colors.primarySoft, borderRadius: 12, marginTop: 10, padding: 12 },
  batchSummaryTitle: { color: ui.colors.primary, fontSize: 12, fontWeight: '800', marginBottom: 4 },
  reviewDraftButton: { alignItems: 'center', backgroundColor: '#FFF7E8', borderColor: '#F5C46B', borderRadius: 12, borderWidth: 1, marginTop: 9, paddingVertical: 13 },
  reviewDraftButtonText: { color: '#A85C00', fontSize: 14, fontWeight: '800' },
  resultCard: { alignItems: 'flex-start', backgroundColor: ui.colors.surface, borderColor: '#EDF0F6', borderRadius: 13, borderWidth: 1, flexDirection: 'row', marginTop: 9, padding: 11, ...ui.subtleShadow },
  resultInfo: { flex: 1 },
  resultTopRow: { alignItems: 'flex-start', flexDirection: 'row', gap: 8 },
  statusPill: { backgroundColor: ui.colors.primarySoft, borderRadius: 8, color: ui.colors.primary, flexShrink: 1, fontSize: 10, fontWeight: '700', overflow: 'hidden', paddingHorizontal: 7, paddingVertical: 4 },
  reviewHint: { color: '#A85C00', fontSize: 12, fontWeight: '700', lineHeight: 18, marginTop: 8 },
  chunkProgressBox: { marginTop: 9 },
  chunkProgressText: { color: ui.colors.mutedText, fontSize: 11, lineHeight: 17 },
  chunkProgressTrack: { backgroundColor: '#DCE6F8', borderRadius: 4, height: 6, marginTop: 6, overflow: 'hidden' },
  chunkProgressFill: { backgroundColor: ui.colors.primary, borderRadius: 4, height: '100%' },
  fileErrorText: { color: ui.colors.danger, fontSize: 11, lineHeight: 17, marginTop: 8 },
  structureButton: { alignItems: 'center', borderColor: '#B7CDFC', borderRadius: 10, borderWidth: 1, marginTop: 12, paddingVertical: 10 },
  retryButtonDisabled: { opacity: 0.55 },
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
