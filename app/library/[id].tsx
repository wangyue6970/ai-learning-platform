import { router, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useState } from 'react';
import { Alert, Modal, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { useLibraries } from '../../contexts/LibraryContext';
import { fetchPracticeQuestions, fetchWrongQuestions, type PracticeQuestion } from '../../services/questionApi';

const questionTypeLabels = {
  single_choice: '单选题',
  multiple_choice: '多选题',
  true_false: '判断题',
};

export default function LibraryDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { libraries, updateLibrary, deleteLibrary: deleteLibraryRequest } = useLibraries();
  const [draftName, setDraftName] = useState('');
  const [isEditModalVisible, setIsEditModalVisible] = useState(false);
  const [libraryQuestions, setLibraryQuestions] = useState<PracticeQuestion[]>([]);
  const [wrongQuestionCount, setWrongQuestionCount] = useState(0);
  const [isQuestionSummaryLoading, setIsQuestionSummaryLoading] = useState(true);
  const [questionSummaryError, setQuestionSummaryError] = useState<string | null>(null);
  const selectedLibrary = libraries.find((library) => library.id === id);

  const reloadQuestionSummary = useCallback(async () => {
    if (!id) {
      return;
    }

    setIsQuestionSummaryLoading(true);
    setQuestionSummaryError(null);
    try {
      const [questions, wrongQuestions] = await Promise.all([
        fetchPracticeQuestions(id),
        fetchWrongQuestions(id),
      ]);
      setLibraryQuestions(questions);
      setWrongQuestionCount(wrongQuestions.length);
    } catch {
      setQuestionSummaryError('题目统计加载失败，请检查后端是否已启动');
    } finally {
      setIsQuestionSummaryLoading(false);
    }
  }, [id]);

  useFocusEffect(
    useCallback(() => {
      void reloadQuestionSummary();
    }, [reloadQuestionSummary])
  );

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

  async function saveLibraryName() {
    const trimmedName = draftName.trim();

    if (!trimmedName) {
      Alert.alert('请输入学习库名称');
      return;
    }

    try {
      await updateLibrary(id, trimmedName);
      setIsEditModalVisible(false);
    } catch {
      Alert.alert('修改失败', '请确认后端正在运行，并且手机和电脑在同一 Wi-Fi。');
    }
  }

  async function deleteLibrary() {
    try {
      await deleteLibraryRequest(id);
      router.back();
    } catch {
      Alert.alert('删除失败', '请确认后端正在运行，并且手机和电脑在同一 Wi-Fi。');
    }
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
      <Text style={styles.meta}>共 {libraryQuestions.length} 题</Text>
      <Text style={styles.meta}>{wrongQuestionCount} 道错题</Text>
      <Text style={styles.questionTitle}>示例题目（{libraryQuestions.length}）</Text>
      {libraryQuestions.length === 0 ? (
        <Text style={styles.emptyText}>暂无题目，后续可通过“导入题目”加入题库。</Text>
      ) : (
        libraryQuestions.map((question) => (
          <Pressable
            key={question.id}
            style={styles.questionCard}
            onPress={() => router.push({
              pathname: '/library/[id]/questions/[questionId]',
              params: { id, questionId: question.id },
            })}>
            <Text style={styles.questionType}>{questionTypeLabels[question.type]}</Text>
            <Text style={styles.questionStem}>{question.stem}</Text>
            <Text style={styles.questionHint}>点击查看题目详情</Text>
          </Pressable>
        ))
      )}
      <Pressable style={styles.actionButton} onPress={openEditModal}>
        <Text style={styles.actionButtonText}>编辑学习库名称</Text>
      </Pressable>
      <Pressable style={styles.actionButton} onPress={confirmDeleteLibrary}>
        <Text style={styles.deleteButtonText}>删除学习库</Text>
      </Pressable>
      <Pressable
        style={styles.primaryButton}
        onPress={() => router.push(`/library/${id}/import`)}>
        <Text style={styles.primaryButtonText}>导入题目</Text>
      </Pressable>
      <Pressable style={styles.actionButton} onPress={() => router.push(`/library/${id}/practice`)}>
        <Text style={styles.actionButtonText}>刷完整题库</Text>
      </Pressable>
      <Pressable
        style={styles.actionButton}
        onPress={() =>
          router.push({ pathname: '/library/[id]/practice', params: { id, mode: 'wrong' } })
        }>
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
  questionTitle: {
    color: '#0F172A',
    fontSize: 18,
    fontWeight: '700',
    marginTop: 28,
  },
  questionCard: {
    backgroundColor: '#F8FAFC',
    borderRadius: 10,
    marginTop: 12,
    padding: 14,
  },
  questionStem: {
    color: '#334155',
    fontSize: 15,
    lineHeight: 22,
  },
  questionType: {
    color: '#2563EB',
    fontSize: 13,
    fontWeight: '700',
    marginBottom: 6,
  },
  questionHint: {
    color: '#64748B',
    fontSize: 13,
    marginTop: 10,
  },
  emptyText: {
    color: '#64748B',
    fontSize: 14,
    lineHeight: 21,
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
