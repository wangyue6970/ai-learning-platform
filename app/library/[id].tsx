import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Alert, Modal, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { useLibraries } from '../../contexts/LibraryContext';

export default function LibraryDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { libraries, setLibraries } = useLibraries();
  const [draftName, setDraftName] = useState('');
  const [isEditModalVisible, setIsEditModalVisible] = useState(false);
  const selectedLibrary = libraries.find((library) => library.id === id);

  function showNextStageMessage(featureName: string) {
    Alert.alert(featureName, '这个功能会在后续阶段实现。');
  }

  if (!selectedLibrary) {
    return null;
  }

  const currentName = selectedLibrary.name;

  function openEditModal() {
    setDraftName(currentName);
    setIsEditModalVisible(true);
  }

  function saveLibraryName() {
    const trimmedName = draftName.trim();

    if (!trimmedName) {
      Alert.alert('请输入学习库名称');
      return;
    }

    setLibraries((currentLibraries) =>
      currentLibraries.map((library) =>
        library.id === id ? { ...library, name: trimmedName } : library
      )
    );
    setIsEditModalVisible(false);
  }

  function deleteLibrary() {
    setLibraries((currentLibraries) =>
      currentLibraries.filter((library) => library.id !== id)
    );
    router.back();
  }

  function confirmDeleteLibrary() {
    Alert.alert('删除学习库', '删除后无法恢复，确定继续吗？', [
      { text: '取消', style: 'cancel' },
      { text: '删除', style: 'destructive', onPress: deleteLibrary },
    ]);
  }

  return (
    <View style={styles.container}>
      <Pressable style={styles.backButton} onPress={() => router.back()}>
        <Text style={styles.backButtonText}>‹ 返回</Text>
      </Pressable>
      <Text style={styles.title}>{selectedLibrary.name}</Text>
      <Text style={styles.meta}>共 {selectedLibrary.questionCount} 题</Text>
      <Text style={styles.meta}>{selectedLibrary.wrongQuestionCount} 道错题</Text>
      <Pressable style={styles.actionButton} onPress={openEditModal}>
        <Text style={styles.actionButtonText}>编辑学习库名称</Text>
      </Pressable>
      <Pressable style={styles.actionButton} onPress={confirmDeleteLibrary}>
        <Text style={styles.deleteButtonText}>删除学习库</Text>
      </Pressable>
      <Pressable
        style={styles.primaryButton}
        onPress={() => showNextStageMessage('导入题目')}>
        <Text style={styles.primaryButtonText}>导入题目</Text>
      </Pressable>
      <Pressable style={styles.actionButton} onPress={() => showNextStageMessage('刷完整题库')}>
        <Text style={styles.actionButtonText}>刷完整题库</Text>
      </Pressable>
      <Pressable style={styles.actionButton} onPress={() => showNextStageMessage('刷错题集')}>
        <Text style={styles.actionButtonText}>刷错题集</Text>
      </Pressable>
      <Modal animationType="fade" transparent visible={isEditModalVisible}>
        <View style={{ flex: 1, justifyContent: 'center', padding: 20 }}>
          <View style={{ backgroundColor: '#FFFFFF', borderRadius: 16, padding: 20 }}>
            <Text>编辑学习库名称</Text>
            <TextInput value={draftName} onChangeText={setDraftName} />
            <Pressable onPress={saveLibraryName}>
              <Text>保存</Text>
            </Pressable>
            <Pressable onPress={() => setIsEditModalVisible(false)}>
              <Text>取消</Text>
            </Pressable>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingHorizontal: 20,
    paddingTop: 64,
  },
  backButton: {
    marginBottom: 24,
  },
  backButtonText: {
    color: '#2563EB',
    fontSize: 16,
  },
  title: {
    fontSize: 26,
    fontWeight: '700',
  },
  meta: {
    fontSize: 16,
    marginTop: 12,
  },
  primaryButton: {
    alignItems: 'center',
    backgroundColor: '#2563EB',
    borderRadius: 10,
    marginTop: 28,
    paddingVertical: 14,
  },
  primaryButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '700',
  },
  actionButton: {
    alignItems: 'center',
    borderColor: '#2563EB',
    borderRadius: 10,
    borderWidth: 1,
    marginTop: 12,
    paddingVertical: 14,
  },
  actionButtonText: {
    color: '#2563EB',
    fontSize: 16,
    fontWeight: '700',
  },
  deleteButtonText: {
    color: '#DC2626',
    fontSize: 16,
    fontWeight: '700',
  },
});
