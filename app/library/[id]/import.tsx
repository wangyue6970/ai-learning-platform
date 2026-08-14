import * as DocumentPicker from 'expo-document-picker';
import * as ImagePicker from 'expo-image-picker';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useLibraries } from '../../../contexts/LibraryContext';
import {
  ImportFileResult,
  recognizeImportFile,
  structureImportFile,
  uploadImportFiles,
} from '../../../services/importApi';

type PendingImportFile = {
  uri: string;
  displayName: string;
  kind: 'image' | 'word';
};

function getImportStatusText(file: ImportFileResult, kind?: PendingImportFile['kind']) {
  switch (file.status) {
    case 'WAITING_RECOGNITION':
      return kind === 'word' ? '已上传，等待 Word 解析' : '已上传，等待识别';
    case 'RECOGNIZING':
      return kind === 'word' ? '正在解析 Word' : '正在识别图片';
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

export default function ImportQuestionsScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { libraries } = useLibraries();
  const library = libraries.find((item) => item.id === id);
  const [selectedFiles, setSelectedFiles] = useState<PendingImportFile[]>([]);
  const [uploading, setUploading] = useState(false);
  const [uploadResults, setUploadResults] = useState<ImportFileResult[]>([]);
  const [fileKinds, setFileKinds] = useState<Record<number, PendingImportFile['kind']>>({});
  const [recognitionProgress, setRecognitionProgress] = useState<{ completed: number; total: number } | null>(null);

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
    addSelectedFiles([{ uri: image.uri, displayName: image.fileName || '拍摄的题目图片', kind: 'image' }]);
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
      kind: 'image' as const,
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

    addSelectedFiles([{ uri: wordFile.uri, displayName: wordFile.name, kind: 'word' }]);
  }

  async function recognizeUploadedFiles(uploadedFiles: ImportFileResult[]) {
    const filesToRecognize = uploadedFiles.filter((file) => file.status === 'WAITING_RECOGNITION');
    const maxConcurrentRecognitions = 3;
    let nextFileIndex = 0;

    setRecognitionProgress({ completed: 0, total: filesToRecognize.length });

    async function recognizeOneFile() {
      while (nextFileIndex < filesToRecognize.length) {
        const currentFileIndex = nextFileIndex;
        nextFileIndex += 1;
        const uploadedFile = filesToRecognize[currentFileIndex];

        try {
          setUploadResults((currentFiles) => currentFiles.map((file) =>
            file.id === uploadedFile.id
              ? { ...file, status: 'RECOGNIZING' as const, errorMessage: null }
              : file
          ));
          const recognizedFile = await recognizeImportFile(id, uploadedFile.id);
          setUploadResults((currentFiles) => currentFiles.map((file) =>
            file.id === recognizedFile.id ? recognizedFile : file
          ));
        } catch (error) {
          const message = error instanceof Error ? error.message : '文件识别失败，请稍后重试';
          setUploadResults((currentFiles) => currentFiles.map((file) =>
            file.id === uploadedFile.id
              ? { ...file, status: 'RECOGNITION_FAILED' as const, errorMessage: message }
              : file
          ));
        } finally {
          setRecognitionProgress((currentProgress) => currentProgress && {
            ...currentProgress,
            completed: currentProgress.completed + 1,
          });
        }
      }
    }

    const workerCount = Math.min(maxConcurrentRecognitions, filesToRecognize.length);
    await Promise.all(Array.from({ length: workerCount }, () => recognizeOneFile()));
  }

  async function uploadSelectedFiles() {
    if (selectedFiles.length === 0 || uploading) {
      return;
    }

    // Keep this upload's file list stable while React state is being updated.
    const filesForThisUpload = selectedFiles;
    setUploading(true);
    setUploadResults([]);
    setRecognitionProgress(null);

    try {
      const result = await uploadImportFiles(id, filesForThisUpload);
      const uploadedKinds = Object.fromEntries(
        result.files.map((file, index) => [file.id, filesForThisUpload[index]?.kind || 'image'])
      ) as Record<number, PendingImportFile['kind']>;
      setFileKinds(uploadedKinds);
      setUploadResults(result.files);
      setSelectedFiles((currentFiles) =>
        currentFiles.filter((_, index) => result.files[index]?.status === 'UPLOAD_FAILED')
      );

      await recognizeUploadedFiles(result.files);
    } catch (error) {
      const message = error instanceof Error ? error.message : '文件上传失败，请稍后重试';
      Alert.alert('上传失败', message);
    } finally {
      setUploading(false);
    }
  }

  async function structureUploadedFile(uploadedFile: ImportFileResult) {
    setUploadResults((currentFiles) => currentFiles.map((file) =>
      file.id === uploadedFile.id
        ? { ...file, status: 'STRUCTURING' as const, errorMessage: null }
        : file
    ));

    try {
      const structuredFile = await structureImportFile(id, uploadedFile.id);
      setUploadResults((currentFiles) => currentFiles.map((file) =>
        file.id === structuredFile.id ? structuredFile : file
      ));
    } catch (error) {
      const message = error instanceof Error ? error.message : '题目草稿生成失败，请稍后重试';
      setUploadResults((currentFiles) => currentFiles.map((file) =>
        file.id === uploadedFile.id
          ? { ...file, status: 'STRUCTURING_FAILED', errorMessage: message }
          : file
      ));
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
      <Pressable style={styles.optionCard} onPress={selectImagesFromLibrary}>
        <Text style={styles.optionTitle}>从相册选择</Text>
        <Text style={styles.optionDescription}>一次选择多张题目图片</Text>
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
            style={[styles.uploadButton, uploading && styles.uploadButtonDisabled]}
            onPress={uploadSelectedFiles}
          >
            <Text style={styles.uploadButtonText}>
              {uploading ? '正在上传和识别…' : `上传 ${selectedFiles.length} 个文件`}
            </Text>
          </Pressable>
        </View>
      )}

      {uploadResults.length > 0 && (
        <View>
          <Text style={styles.sectionTitle}>导入结果</Text>
          {recognitionProgress && (
            <Text style={styles.progressText}>
              识别进度：{recognitionProgress.completed} / {recognitionProgress.total}
            </Text>
          )}
          {uploadResults.map((file) => (
            <View key={file.id} style={styles.resultCard}>
              <Text style={styles.fileName}>
                {file.originalFileName}：{getImportStatusText(file, fileKinds[file.id])}
              </Text>
              {file.status === 'WAITING_STRUCTURING' && (
                <Pressable
                  style={styles.structureButton}
                  onPress={() => structureUploadedFile(file)}
                >
                  <Text style={styles.structureButtonText}>生成题目草稿</Text>
                </Pressable>
              )}
              {file.status === 'WAITING_CONFIRMATION' && (
                <Pressable
                  style={styles.structureButton}
                  onPress={() => router.push({
                    pathname: '/library/[id]/drafts/[importFileId]',
                    params: { id, importFileId: String(file.id) },
                  })}
                >
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
  sectionTitle: { color: '#0F172A', fontSize: 18, fontWeight: '700', marginTop: 32 },
  optionCard: { backgroundColor: '#FFFFFF', borderColor: '#E2E8F0', borderRadius: 12, borderWidth: 1, marginTop: 12, padding: 18 },
  optionTitle: { color: '#0F172A', fontSize: 17, fontWeight: '700' },
  optionDescription: { color: '#64748B', fontSize: 14, marginTop: 6 },
  fileName: { backgroundColor: '#FFFFFF', borderRadius: 8, color: '#334155', marginTop: 8, padding: 12 },
  resultCard: { backgroundColor: '#FFFFFF', borderRadius: 8, marginTop: 8, padding: 12 },
  structureButton: { alignItems: 'center', borderColor: '#2563EB', borderRadius: 8, borderWidth: 1, marginTop: 10, paddingVertical: 10 },
  structureButtonText: { color: '#2563EB', fontSize: 14, fontWeight: '700' },
  uploadButton: { alignItems: 'center', backgroundColor: '#2563EB', borderRadius: 10, marginTop: 16, paddingVertical: 14 },
  uploadButtonDisabled: { backgroundColor: '#93C5FD' },
  uploadButtonText: { color: '#FFFFFF', fontSize: 15, fontWeight: '700' },
  progressText: { color: '#64748B', fontSize: 14, marginTop: 10 },
  emptyText: { flex: 1, padding: 20, paddingTop: 64 },
});
