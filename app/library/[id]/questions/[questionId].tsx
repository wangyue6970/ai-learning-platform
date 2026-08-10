import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { fetchQuestionDetail, type EditableQuestion, updateQuestion } from '../../../../services/questionApi';

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
  const [question, setQuestion] = useState<EditableQuestion | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isEditing, setIsEditing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [draftStem, setDraftStem] = useState('');
  const [draftOptions, setDraftOptions] = useState<Array<{ id: string; text: string }>>([]);
  const [draftCorrectAnswer, setDraftCorrectAnswer] = useState<string[]>([]);
  const [draftExplanation, setDraftExplanation] = useState('');

  useEffect(() => {
    async function loadQuestion() {
      try {
        setQuestion(await fetchQuestionDetail(questionId));
      } catch {
        setError('题目详情加载失败，请检查后端是否已启动。');
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
      Alert.alert('无法保存', '请填写题干、全部选项，并选择正确答案。');
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
    } catch {
      Alert.alert('保存失败', '请检查后端是否已启动后重试。');
    } finally {
      setIsSaving(false);
    }
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
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { backgroundColor: '#FFFFFF', flexGrow: 1, padding: 20, paddingTop: 64 },
  centerState: { alignItems: 'center', flex: 1, justifyContent: 'center', padding: 24 },
  backText: { color: '#2563EB', fontSize: 16, marginBottom: 26 },
  titleRow: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  type: { color: '#2563EB', fontSize: 14, fontWeight: '700' },
  editText: { color: '#2563EB', fontSize: 15, fontWeight: '700' },
  stem: { color: '#0F172A', fontSize: 20, fontWeight: '700', lineHeight: 30, marginTop: 10 },
  stemInput: { borderColor: '#2563EB', borderRadius: 10, borderWidth: 1, color: '#0F172A', fontSize: 20, fontWeight: '700', lineHeight: 30, marginTop: 10, minHeight: 100, padding: 12, textAlignVertical: 'top' },
  optionCard: { borderColor: '#CBD5E1', borderRadius: 10, borderWidth: 1, marginTop: 12, padding: 14 },
  correctOptionCard: { backgroundColor: '#DCFCE7', borderColor: '#16A34A' },
  optionText: { color: '#334155', fontSize: 16, lineHeight: 24 },
  optionInput: { color: '#334155', fontSize: 16, lineHeight: 24, padding: 0 },
  choiceText: { color: '#2563EB', fontSize: 14, fontWeight: '700', marginTop: 10 },
  correctChoiceText: { color: '#15803D', fontSize: 14, fontWeight: '700', marginTop: 10 },
  sectionTitle: { color: '#0F172A', fontSize: 16, fontWeight: '700', marginTop: 28 },
  explanationText: { color: '#334155', fontSize: 16, lineHeight: 25, marginTop: 8 },
  explanationInput: { borderColor: '#CBD5E1', borderRadius: 10, borderWidth: 1, color: '#334155', fontSize: 16, lineHeight: 25, marginTop: 8, minHeight: 100, padding: 12, textAlignVertical: 'top' },
  actionRow: { flexDirection: 'row', gap: 12, marginTop: 28 },
  cancelButton: { alignItems: 'center', borderColor: '#94A3B8', borderRadius: 10, borderWidth: 1, flex: 1, padding: 14 },
  cancelButtonText: { color: '#475569', fontWeight: '700' },
  saveButton: { alignItems: 'center', backgroundColor: '#2563EB', borderRadius: 10, flex: 1, padding: 14 },
  saveButtonText: { color: '#FFFFFF', fontWeight: '700' },
});
