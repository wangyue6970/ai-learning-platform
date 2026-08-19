import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { deleteQuestion, fetchQuestionDetail, type EditableQuestion, updateQuestion } from '../../../../services/questionApi';
import { ui } from '../../../../constants/ui';
import { useDialog } from '../../../../components/AppDialog';

const questionTypeLabels = {
  single_choice: '单选题',
  multiple_choice: '多选题',
  true_false: '判断题',
};

const backendQuestionTypes = {
  single_choice: 'SINGLE_CHOICE',
  multiple_choice: 'MULTIPLE_CHOICE',
  true_false: 'TRUE_FALSE',
} as const;

export default function QuestionDetailScreen() {
  const { questionId } = useLocalSearchParams<{ questionId: string }>();
  const { showDialog } = useDialog();
  const [question, setQuestion] = useState<EditableQuestion | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isEditing, setIsEditing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [draftStem, setDraftStem] = useState('');
  const [draftOptions, setDraftOptions] = useState<Array<{ id: string; text: string }>>([]);
  const [draftCorrectAnswer, setDraftCorrectAnswer] = useState<string[]>([]);
  const [draftExplanation, setDraftExplanation] = useState('');

  useEffect(() => {
    async function loadQuestion() {
      try {
        setQuestion(await fetchQuestionDetail(questionId));
      } catch (loadError) {
        setError(loadError instanceof Error ? loadError.message : '题目详情加载失败，请检查后端是否已启动。');
      }
    }

    void loadQuestion();
  }, [questionId]);

  function startEditing() {
    if (!question) {
      return;
    }
    setDraftStem(question.stem);
    setDraftOptions(question.options);
    setDraftCorrectAnswer(question.correctAnswer);
    setDraftExplanation(question.explanation || '');
    setIsEditing(true);
  }

  function updateOptionText(optionId: string, text: string) {
    setDraftOptions((currentOptions) => currentOptions.map((option) => (
      option.id === optionId ? { ...option, text } : option
    )));
  }

  function toggleCorrectAnswer(optionId: string) {
    if (!question) {
      return;
    }
    if (question.type !== 'multiple_choice') {
      setDraftCorrectAnswer([optionId]);
      return;
    }
    setDraftCorrectAnswer((currentAnswers) => (
      currentAnswers.includes(optionId)
        ? currentAnswers.filter((answer) => answer !== optionId)
        : [...currentAnswers, optionId]
    ));
  }

  async function saveQuestion() {
    if (!question) {
      return;
    }
    if (!draftStem.trim() || draftOptions.some((option) => !option.text.trim()) || draftCorrectAnswer.length === 0) {
      showDialog({ title: '无法保存', message: '请填写题干、全部选项，并选择正确答案。', tone: 'warning' });
      return;
    }

    setIsSaving(true);
    try {
      const updatedQuestion = await updateQuestion(questionId, {
        questionType: backendQuestionTypes[question.type],
        stem: draftStem.trim(),
        options: draftOptions.map((option, index) => ({
          optionKey: option.id,
          content: option.text.trim(),
          sortOrder: index + 1,
        })),
        correctAnswer: draftCorrectAnswer,
        explanation: draftExplanation.trim() || null,
      });
      setQuestion(updatedQuestion);
      setIsEditing(false);
    } catch (saveError) {
      const message = saveError instanceof Error ? saveError.message : '请检查后端是否已启动后重试。';
      showDialog({ title: '保存失败', message, tone: 'warning', primaryLabel: '继续修改' });
    } finally {
      setIsSaving(false);
    }
  }

  async function deleteCurrentQuestion() {
    setIsDeleting(true);
    try {
      await deleteQuestion(questionId);
      router.back();
    } catch (deleteError) {
      const message = deleteError instanceof Error ? deleteError.message : '请检查后端是否已启动后重试。';
      showDialog({ title: '删除失败', message, tone: 'danger' });
    } finally {
      setIsDeleting(false);
    }
  }

  function confirmDeleteQuestion() {
    showDialog({
      title: '确认删除题目？',
      message: '删除后会同时清除相关作答记录和错题状态，无法恢复。',
      tone: 'danger',
      secondaryLabel: '取消',
      primaryLabel: '删除',
      primaryVariant: 'danger',
      onPrimary: () => void deleteCurrentQuestion(),
    });
  }

  if (error) {
    return <View style={styles.centerState}><Text>{error}</Text></View>;
  }

  if (!question) {
    return <View style={styles.centerState}><Text>题目详情加载中...</Text></View>;
  }

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Pressable onPress={() => router.back()}>
        <Text style={styles.backText}>‹ 返回学习库</Text>
      </Pressable>
      <View style={styles.titleRow}>
        <Text style={styles.type}>{questionTypeLabels[question.type]}</Text>
        {!isEditing && <Pressable onPress={startEditing}><Text style={styles.editText}>编辑题目</Text></Pressable>}
      </View>

      {isEditing ? (
        <TextInput multiline style={styles.stemInput} value={draftStem} onChangeText={setDraftStem} />
      ) : (
        <Text style={styles.stem}>{question.stem}</Text>
      )}

      {(isEditing ? draftOptions : question.options).map((option) => {
        const isCorrectAnswer = (isEditing ? draftCorrectAnswer : question.correctAnswer).includes(option.id);
        return (
          <View key={option.id} style={[styles.optionCard, isCorrectAnswer && styles.correctOptionCard]}>
            {isEditing ? (
              <>
                <TextInput
                  style={styles.optionInput}
                  value={option.text}
                  onChangeText={(text) => updateOptionText(option.id, text)}
                />
                <Pressable onPress={() => toggleCorrectAnswer(option.id)}>
                  <Text style={isCorrectAnswer ? styles.correctChoiceText : styles.choiceText}>
                    {isCorrectAnswer ? '✓ 正确答案' : '设为正确答案'}
                  </Text>
                </Pressable>
              </>
            ) : (
              <Text style={styles.optionText}>{option.id}. {option.text}</Text>
            )}
          </View>
        );
      })}

      <Text style={styles.sectionTitle}>解析</Text>
      {isEditing ? (
        <TextInput
          multiline
          placeholder="可选填写解析"
          style={styles.explanationInput}
          value={draftExplanation}
          onChangeText={setDraftExplanation}
        />
      ) : (
        <Text style={styles.explanationText}>{question.explanation || '暂无解析'}</Text>
      )}

      {isEditing && (
        <View style={styles.actionRow}>
          <Pressable style={styles.cancelButton} onPress={() => setIsEditing(false)} disabled={isSaving}>
            <Text style={styles.cancelButtonText}>取消</Text>
          </Pressable>
          <Pressable style={styles.saveButton} onPress={() => void saveQuestion()} disabled={isSaving}>
            <Text style={styles.saveButtonText}>{isSaving ? '保存中...' : '保存修改'}</Text>
          </Pressable>
        </View>
      )}
      {!isEditing && (
        <Pressable style={styles.deleteButton} onPress={confirmDeleteQuestion} disabled={isDeleting}>
          <Text style={styles.deleteButtonText}>{isDeleting ? '删除中...' : '删除题目'}</Text>
        </Pressable>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { backgroundColor: ui.colors.background, flexGrow: 1, padding: 20, paddingBottom: 48, paddingTop: 58 },
  centerState: { alignItems: 'center', backgroundColor: ui.colors.background, flex: 1, justifyContent: 'center', padding: 24 },
  backText: { color: ui.colors.primary, fontSize: 16, fontWeight: '700', marginBottom: 26 },
  titleRow: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  type: { backgroundColor: ui.colors.primarySoft, borderRadius: 8, color: ui.colors.primary, fontSize: 13, fontWeight: '800', overflow: 'hidden', paddingHorizontal: 9, paddingVertical: 5 },
  editText: { color: ui.colors.primary, fontSize: 15, fontWeight: '800' },
  stem: { color: ui.colors.text, fontSize: 21, fontWeight: '800', lineHeight: 31, marginTop: 14 },
  stemInput: { backgroundColor: ui.colors.surface, borderColor: ui.colors.primary, borderRadius: 14, borderWidth: 1, color: ui.colors.text, fontSize: 20, fontWeight: '800', lineHeight: 30, marginTop: 14, minHeight: 100, padding: 14, textAlignVertical: 'top' },
  optionCard: { backgroundColor: ui.colors.surface, borderColor: ui.colors.border, borderRadius: 14, borderWidth: 1, marginTop: 12, padding: 15, ...ui.subtleShadow },
  correctOptionCard: { backgroundColor: ui.colors.successSoft, borderColor: ui.colors.success },
  optionText: { color: ui.colors.text, fontSize: 16, lineHeight: 24 },
  optionInput: { color: ui.colors.text, fontSize: 16, lineHeight: 24, padding: 0 },
  choiceText: { color: ui.colors.primary, fontSize: 14, fontWeight: '800', marginTop: 10 },
  correctChoiceText: { color: ui.colors.success, fontSize: 14, fontWeight: '800', marginTop: 10 },
  sectionTitle: { color: ui.colors.text, fontSize: 17, fontWeight: '800', marginTop: 28 },
  explanationText: { color: ui.colors.mutedText, fontSize: 16, lineHeight: 25, marginTop: 9 },
  explanationInput: { backgroundColor: ui.colors.surface, borderColor: ui.colors.border, borderRadius: 14, borderWidth: 1, color: ui.colors.text, fontSize: 16, lineHeight: 25, marginTop: 9, minHeight: 100, padding: 13, textAlignVertical: 'top' },
  actionRow: { flexDirection: 'row', gap: 12, marginTop: 28 },
  cancelButton: { alignItems: 'center', backgroundColor: ui.colors.disabledSoft, borderRadius: 13, flex: 1, padding: 14 },
  cancelButtonText: { color: ui.colors.mutedText, fontWeight: '800' },
  saveButton: { alignItems: 'center', backgroundColor: ui.colors.primary, borderRadius: 13, flex: 1, padding: 14, ...ui.shadow },
  saveButtonText: { color: '#FFFFFF', fontWeight: '800' },
  deleteButton: { alignItems: 'center', backgroundColor: ui.colors.dangerSoft, borderColor: '#FFC7CE', borderRadius: 13, borderWidth: 1, marginTop: 28, padding: 14 },
  deleteButtonText: { color: ui.colors.danger, fontWeight: '800' },
});
