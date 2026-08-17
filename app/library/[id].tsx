import { router, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useState } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { useLibraries } from '../../contexts/LibraryContext';
import { fetchPracticeQuestions, fetchWrongQuestions, type PracticeQuestion } from '../../services/questionApi';
import { ui } from '../../constants/ui';
import { useDialog } from '../../components/AppDialog';

const questionTypeLabels = {
  single_choice: '单选题',
  multiple_choice: '多选题',
  true_false: '判断题',
};

export default function LibraryDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { libraries, updateLibrary, deleteLibrary: deleteLibraryRequest } = useLibraries();
  const { showDialog } = useDialog();
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
      showDialog({ title: '请输入学习库名称', message: '名称不能为空。', tone: 'warning' });
      return;
    }

    try {
      await updateLibrary(id, trimmedName);
      setIsEditModalVisible(false);
    } catch {
      showDialog({ title: '修改失败', message: '请确认后端正在运行，并且手机和电脑在同一 Wi-Fi。', tone: 'danger' });
    }
  }

  async function deleteLibrary() {
    try {
      await deleteLibraryRequest(id);
      router.back();
    } catch {
      showDialog({ title: '删除失败', message: '请确认后端正在运行，并且手机和电脑在同一 Wi-Fi。', tone: 'danger' });
    }
  }

  function confirmDeleteLibrary() {
    showDialog({
      title: '确认删除学习库？',
      message: '删除后，学习库中的题目、作答记录和错题状态都会被清除，无法恢复。',
      tone: 'danger',
      secondaryLabel: '取消',
      primaryLabel: '删除',
      primaryVariant: 'danger',
      onPrimary: () => void deleteLibrary(),
    });
  }

  return (
    <View style={styles.screen}>
      <ScrollView contentContainerStyle={styles.container}>
        <Pressable style={styles.backButton} onPress={() => router.back()}>
          <Text style={styles.backButtonText}>‹ 返回</Text>
        </Pressable>
        <View style={styles.libraryHero}>
          <View style={styles.heroFolder}>
            <View style={styles.heroFolderTab} />
            <View style={styles.heroFolderBody} />
          </View>
          <View style={styles.heroInfo}>
            <Text style={styles.title}>{selectedLibrary.name}</Text>
            <Text style={styles.meta}>
              {isQuestionSummaryLoading
                ? '题目统计加载中...'
                : `共 ${libraryQuestions.length} 题 · ${wrongQuestionCount} 道错题`}
            </Text>
          </View>
        </View>
        {questionSummaryError && <Text style={styles.errorText}>{questionSummaryError}</Text>}

        <View style={styles.primaryActionGroup}>
          <Pressable
            style={styles.primaryButton}
            onPress={() => router.push(`/library/${id}/import`)}>
            <Text style={styles.primaryButtonText}>导入题目</Text>
          </Pressable>
          <View style={styles.quickActionRow}>
            <Pressable style={styles.actionButton} onPress={() => router.push(`/library/${id}/practice`)}>
              <Text style={styles.actionButtonText}>☷ 刷完整题库</Text>
            </Pressable>
            <Pressable
              disabled={isQuestionSummaryLoading || wrongQuestionCount === 0}
              style={[
                styles.actionButton,
                (isQuestionSummaryLoading || wrongQuestionCount === 0) && styles.disabledActionButton,
              ]}
              onPress={() =>
                router.push({ pathname: '/library/[id]/practice', params: { id, mode: 'wrong' } })
              }>
              <Text
                style={[
                  styles.actionButtonText,
                  (isQuestionSummaryLoading || wrongQuestionCount === 0) && styles.disabledActionButtonText,
                ]}>
                ✕ 刷错题集（{isQuestionSummaryLoading ? '...' : wrongQuestionCount}）
              </Text>
            </Pressable>
          </View>
        </View>

        <View style={styles.previewTitleRow}>
          <Text style={styles.questionTitle}>题目预览（{libraryQuestions.length}）</Text>
          {libraryQuestions.length > 0 && (
            <Pressable onPress={() => router.push(`/library/${id}/questions`)}>
              <Text style={styles.viewAllText}>查看全部 ›</Text>
            </Pressable>
          )}
        </View>
        <Text style={styles.previewHint}>这里只显示最多 3 道题；全部题目将在下一页查看。</Text>
        {libraryQuestions.length === 0 ? (
          <Text style={styles.emptyText}>暂无题目，可通过“导入题目”加入题库。</Text>
        ) : (
          libraryQuestions.slice(0, 3).map((question) => (
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
        {libraryQuestions.length > 3 && (
          <Text style={styles.previewHint}>还有 {libraryQuestions.length - 3} 道题将在“全部题目”页查看。</Text>
        )}

        <View style={styles.managementSection}>
          <Text style={styles.managementTitle}>学习库管理</Text>
          <Pressable style={[styles.actionButton, styles.managementButton]} onPress={openEditModal}>
            <Text style={styles.actionButtonText}>编辑学习库名称</Text>
          </Pressable>
          <Pressable style={[styles.actionButton, styles.managementButton]} onPress={confirmDeleteLibrary}>
            <Text style={styles.deleteButtonText}>删除学习库</Text>
          </Pressable>
        </View>
      </ScrollView>
      <Modal animationType="fade" transparent visible={isEditModalVisible}>
        <View style={styles.modalBackdrop}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>编辑学习库名称</Text>
            <Text style={styles.modalHint}>更改名称不会影响题目、错题或练习记录。</Text>
            <TextInput style={styles.modalInput} value={draftName} onChangeText={setDraftName} />
            <View style={styles.modalButtonRow}>
              <Pressable style={styles.modalCancelButton} onPress={() => setIsEditModalVisible(false)}>
                <Text style={styles.modalCancelText}>取消</Text>
              </Pressable>
              <Pressable style={styles.modalSaveButton} onPress={saveLibraryName}>
                <Text style={styles.modalSaveText}>保存</Text>
              </Pressable>
            </View>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    backgroundColor: ui.colors.background,
    flex: 1,
  },
  container: {
    paddingHorizontal: 20,
    paddingTop: 64,
    paddingBottom: 48,
  },
  backButton: {
    marginBottom: 24,
  },
  backButtonText: {
    color: ui.colors.primary,
    fontSize: 16,
    fontWeight: '700',
  },
  libraryHero: { alignItems: 'center', flexDirection: 'row', marginTop: 4 },
  heroFolder: { backgroundColor: '#EAF1FF', borderRadius: 16, height: 60, overflow: 'hidden', position: 'relative', width: 60 },
  heroFolderTab: { backgroundColor: '#85ADFF', borderRadius: 5, height: 14, left: 10, position: 'absolute', top: 11, width: 26 },
  heroFolderBody: { backgroundColor: ui.colors.primary, borderRadius: 9, bottom: 11, left: 8, position: 'absolute', right: 8, top: 22 },
  heroInfo: { flex: 1, marginLeft: 14 },
  title: {
    color: ui.colors.text,
    fontSize: 25,
    fontWeight: '800',
  },
  meta: {
    color: ui.colors.mutedText,
    fontSize: 13,
    marginTop: 6,
  },
  errorText: {
    color: '#DC2626',
    fontSize: 14,
    marginTop: 10,
  },
  primaryActionGroup: {
    marginTop: 24,
  },
  quickActionRow: { flexDirection: 'row', gap: 10, marginTop: 10 },
  questionTitle: {
    color: '#0F172A',
    fontSize: 18,
    fontWeight: '700',
    marginTop: 32,
  },
  previewTitleRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  viewAllText: {
    color: '#2563EB',
    fontSize: 14,
    fontWeight: '700',
    marginTop: 32,
  },
  previewHint: {
    color: '#64748B',
    fontSize: 13,
    lineHeight: 20,
    marginTop: 8,
  },
  questionCard: {
    backgroundColor: ui.colors.surface,
    borderColor: ui.colors.border,
    borderRadius: ui.radius.card,
    borderWidth: 1,
    marginTop: 12,
    padding: 14,
    ...ui.subtleShadow,
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
    backgroundColor: ui.colors.primary,
    borderRadius: ui.radius.button,
    marginTop: 0,
    paddingVertical: 15,
    ...ui.shadow,
  },
  primaryButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '700',
  },
  actionButton: {
    alignItems: 'center',
    backgroundColor: ui.colors.surface,
    borderColor: '#B7CDFC',
    borderRadius: ui.radius.small,
    borderWidth: 1,
    flex: 1,
    marginTop: 0,
    paddingHorizontal: 6,
    paddingVertical: 12,
  },
  actionButtonText: {
    color: ui.colors.primary,
    fontSize: 13,
    fontWeight: '800',
  },
  disabledActionButton: {
    backgroundColor: ui.colors.disabledSoft,
    borderColor: ui.colors.border,
  },
  disabledActionButtonText: {
    color: ui.colors.disabled,
  },
  managementSection: {
    marginTop: 32,
  },
  managementTitle: {
    color: '#0F172A',
    fontSize: 18,
    fontWeight: '700',
  },
  managementButton: { flex: 0, marginTop: 12 },
  deleteButtonText: {
    color: ui.colors.danger,
    fontSize: 15,
    fontWeight: '800',
  },
  modalBackdrop: { backgroundColor: ui.colors.overlay, flex: 1, justifyContent: 'center', padding: 22 },
  modalCard: { backgroundColor: ui.colors.surface, borderRadius: 22, padding: 22, ...ui.shadow },
  modalTitle: { color: ui.colors.text, fontSize: 20, fontWeight: '800' },
  modalHint: { color: ui.colors.mutedText, fontSize: 13, lineHeight: 20, marginTop: 8 },
  modalInput: { backgroundColor: ui.colors.background, borderColor: ui.colors.border, borderRadius: 12, borderWidth: 1, color: ui.colors.text, fontSize: 16, marginTop: 18, padding: 14 },
  modalButtonRow: { flexDirection: 'row', gap: 10, marginTop: 16 },
  modalCancelButton: { alignItems: 'center', backgroundColor: ui.colors.disabledSoft, borderRadius: 12, flex: 1, paddingVertical: 13 },
  modalCancelText: { color: ui.colors.mutedText, fontWeight: '800' },
  modalSaveButton: { alignItems: 'center', backgroundColor: ui.colors.primary, borderRadius: 12, flex: 1, paddingVertical: 13 },
  modalSaveText: { color: '#FFFFFF', fontWeight: '800' },
});
