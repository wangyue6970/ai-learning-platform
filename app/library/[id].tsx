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
    } catch (updateError) {
      const message = updateError instanceof Error
        ? updateError.message
        : '请确认后端正在运行，并且手机和电脑在同一 Wi-Fi。';
      showDialog({ title: '修改失败', message, tone: 'warning', primaryLabel: '继续修改' });
    }
  }

  async function deleteLibrary() {
    try {
      await deleteLibraryRequest(id);
      router.back();
    } catch (deleteError) {
      const message = deleteError instanceof Error
        ? deleteError.message
        : '请确认后端正在运行，并且手机和电脑在同一 Wi-Fi。';
      showDialog({ title: '删除失败', message, tone: 'danger' });
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
            <Text style={styles.primaryButtonText}>⇧  导入题目</Text>
          </Pressable>
          <View style={styles.quickActionRow}>
            <Pressable style={styles.actionButton} onPress={() => router.push(`/library/${id}/practice`)}>
              <Text style={styles.actionButtonText}>☷  刷完整题库</Text>
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
                ✕  刷错题集（{isQuestionSummaryLoading ? '...' : wrongQuestionCount}）
              </Text>
            </Pressable>
          </View>
        </View>

        <View style={styles.previewTitleRow}>
          <Text style={styles.questionTitle}>题目预览（最新 3 题）</Text>
          {libraryQuestions.length > 0 && (
            <Pressable onPress={() => router.push(`/library/${id}/questions`)}>
              <Text style={styles.viewAllText}>查看全部 ›</Text>
            </Pressable>
          )}
        </View>
        {libraryQuestions.length === 0 ? (
          <Text style={styles.emptyText}>暂无题目，可通过“导入题目”加入题库。</Text>
        ) : (
          libraryQuestions.slice(0, 3).map((question, index) => (
            <Pressable
              key={question.id}
              style={styles.questionCard}
              onPress={() => router.push({
                pathname: '/library/[id]/questions/[questionId]',
                params: { id, questionId: question.id },
              })}>
              <View style={styles.questionNumber}><Text style={styles.questionNumberText}>{index + 1}</Text></View>
              <View style={styles.questionPreviewInfo}>
                <Text numberOfLines={1} style={styles.questionStem}>{question.stem}</Text>
                <Text style={styles.questionType}>{questionTypeLabels[question.type]}</Text>
              </View>
              <Text style={styles.questionHint}>›</Text>
            </Pressable>
          ))
        )}
        {libraryQuestions.length > 3 && (
          <Text style={styles.previewHint}>还有 {libraryQuestions.length - 3} 道题将在“全部题目”页查看。</Text>
        )}

        <View style={styles.managementSection}>
          <Text style={styles.managementTitle}>学习库管理</Text>
          <View style={styles.managementGrid}>
            <Pressable style={styles.managementTile} onPress={() => router.push(`/library/${id}/questions`)}>
              <Text style={styles.managementIcon}>☷</Text>
              <Text style={styles.managementLabel}>题目管理</Text>
            </Pressable>
            <Pressable
              disabled={isQuestionSummaryLoading || wrongQuestionCount === 0}
              style={[styles.managementTile, (isQuestionSummaryLoading || wrongQuestionCount === 0) && styles.disabledManagementTile]}
              onPress={() => router.push({ pathname: '/library/[id]/practice', params: { id, mode: 'wrong' } })}>
              <Text style={[styles.managementIcon, (isQuestionSummaryLoading || wrongQuestionCount === 0) && styles.disabledManagementText]}>✕</Text>
              <Text style={[styles.managementLabel, (isQuestionSummaryLoading || wrongQuestionCount === 0) && styles.disabledManagementText]}>错题集</Text>
            </Pressable>
            <Pressable style={styles.managementTile} onPress={openEditModal}>
              <Text style={styles.managementIcon}>✎</Text>
              <Text style={styles.managementLabel}>重命名</Text>
            </Pressable>
            <Pressable style={styles.managementTile} onPress={confirmDeleteLibrary}>
              <Text style={[styles.managementIcon, styles.deleteButtonText]}>⌫</Text>
              <Text style={[styles.managementLabel, styles.deleteButtonText]}>删除</Text>
            </Pressable>
          </View>
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
    paddingHorizontal: 18,
    paddingTop: 56,
    paddingBottom: 48,
  },
  backButton: { marginBottom: 20 },
  backButtonText: {
    color: ui.colors.primary,
    fontSize: 16,
    fontWeight: '700',
  },
  libraryHero: { alignItems: 'center', flexDirection: 'row', marginTop: 2 },
  heroFolder: { backgroundColor: '#EAF1FF', borderRadius: 14, height: 54, overflow: 'hidden', position: 'relative', width: 54 },
  heroFolderTab: { backgroundColor: '#85ADFF', borderRadius: 5, height: 12, left: 9, position: 'absolute', top: 9, width: 24 },
  heroFolderBody: { backgroundColor: ui.colors.primary, borderRadius: 8, bottom: 9, left: 7, position: 'absolute', right: 7, top: 20 },
  heroInfo: { flex: 1, marginLeft: 13 },
  title: {
    color: ui.colors.text,
    fontSize: 22,
    fontWeight: '800',
  },
  meta: {
    color: ui.colors.mutedText,
    fontSize: 12,
    marginTop: 6,
  },
  errorText: {
    color: '#DC2626',
    fontSize: 14,
    marginTop: 10,
  },
  primaryActionGroup: {
    marginTop: 21,
  },
  quickActionRow: { flexDirection: 'row', gap: 9, marginTop: 9 },
  questionTitle: {
    color: '#0F172A',
    fontSize: 17,
    fontWeight: '700',
    marginTop: 29,
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
    marginTop: 29,
  },
  questionCard: {
    alignItems: 'center',
    backgroundColor: ui.colors.surface,
    borderColor: ui.colors.border,
    borderRadius: 12,
    borderWidth: 1,
    flexDirection: 'row',
    marginTop: 12,
    minHeight: 56,
    paddingHorizontal: 12,
    paddingVertical: 10,
    ...ui.subtleShadow,
  },
  questionNumber: { alignItems: 'center', backgroundColor: '#F1F5FB', borderRadius: 12, height: 24, justifyContent: 'center', width: 24 },
  questionNumberText: { color: ui.colors.mutedText, fontSize: 12, fontWeight: '800' },
  questionPreviewInfo: { flex: 1, marginLeft: 10 },
  questionStem: {
    color: ui.colors.text,
    fontSize: 13,
    fontWeight: '700',
  },
  questionType: {
    color: '#2563EB',
    fontSize: 11,
    fontWeight: '700',
    marginTop: 4,
  },
  questionHint: {
    color: '#93A1B5',
    fontSize: 24,
    lineHeight: 28,
    marginLeft: 8,
  },
  previewHint: { color: ui.colors.mutedText, fontSize: 12, marginTop: 10 },
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
  managementSection: { marginTop: 30 },
  managementTitle: {
    color: '#0F172A',
    fontSize: 17,
    fontWeight: '700',
  },
  managementGrid: { flexDirection: 'row', gap: 8, marginTop: 12 },
  managementTile: { alignItems: 'center', backgroundColor: ui.colors.surface, borderColor: ui.colors.border, borderRadius: 12, borderWidth: 1, flex: 1, minHeight: 74, justifyContent: 'center', paddingHorizontal: 4, ...ui.subtleShadow },
  managementIcon: { color: ui.colors.primary, fontSize: 19, fontWeight: '800' },
  managementLabel: { color: ui.colors.text, fontSize: 10, fontWeight: '700', marginTop: 7 },
  disabledManagementTile: { backgroundColor: ui.colors.disabledSoft },
  disabledManagementText: { color: ui.colors.disabled },
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
