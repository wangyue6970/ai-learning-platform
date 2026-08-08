import { StatusBar } from 'expo-status-bar';
import { router, useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { Alert, FlatList, Modal, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { useLibraries } from '../contexts/LibraryContext';
import { fetchPracticeQuestions, fetchWrongQuestions } from '../services/questionApi';

type LibraryQuestionSummary = {
  questionCount: number;
  wrongQuestionCount: number;
};

export default function LibraryListScreen() {
  const { libraries, isLoading, error, createLibrary } = useLibraries();
  const [summaries, setSummaries] = useState<Record<string, LibraryQuestionSummary>>({});
  const [draftName, setDraftName] = useState('');
  const [isCreateModalVisible, setIsCreateModalVisible] = useState(false);

  const reloadSummaries = useCallback(async () => {
    const summaryEntries = await Promise.all(
      libraries.map(async (library) => {
        const [questions, wrongQuestions] = await Promise.all([
          fetchPracticeQuestions(library.id),
          fetchWrongQuestions(library.id),
        ]);

        return [library.id, { questionCount: questions.length, wrongQuestionCount: wrongQuestions.length }] as const;
      })
    );

    setSummaries(Object.fromEntries(summaryEntries));
  }, [libraries]);

  useFocusEffect(
    useCallback(() => {
      void reloadSummaries().catch(() => setSummaries({}));
    }, [reloadSummaries])
  );

  async function createNewLibrary() {
    const trimmedName = draftName.trim();

    if (!trimmedName) {
      Alert.alert('请输入学习库名称');
      return;
    }

    try {
      await createLibrary(trimmedName);
      setDraftName('');
      setIsCreateModalVisible(false);
    } catch {
      Alert.alert('创建失败', '请确认后端正在运行，并且手机和电脑在同一 Wi-Fi。');
    }
  }

  return (
    <View style={styles.container}>
      <StatusBar style="dark" />
      <FlatList
        data={libraries}
        keyExtractor={(library) => library.id}
        renderItem={({ item }) => {
          const summary = summaries[item.id];

          return (
            <Pressable style={styles.libraryCard} onPress={() => router.push(`/library/${item.id}`)}>
              <View style={styles.iconBox}>
                <Text style={styles.iconText}>库</Text>
              </View>
              <View style={styles.libraryInfo}>
                <Text style={styles.libraryName}>{item.name}</Text>
                <Text style={styles.libraryMeta}>
                  {summary ? `共 ${summary.questionCount} 题 · ${summary.wrongQuestionCount} 道错题` : '题目统计加载中...'}
                </Text>
              </View>
            </Pressable>
          );
        }}
        ListHeaderComponent={
          <View style={styles.header}>
            <Text style={styles.title}>我的学习库</Text>
            <Text style={styles.subtitle}>题目、错题和练习记录都会归属于一个学习库。</Text>
          </View>
        }
        ListFooterComponent={
          <Pressable style={styles.createButton} onPress={() => setIsCreateModalVisible(true)}>
            <Text style={styles.createButtonText}>＋ 新建学习库</Text>
          </Pressable>
        }
        ListEmptyComponent={
          <Text style={styles.emptyText}>{isLoading ? '正在加载学习库...' : error ?? '暂时没有学习库'}</Text>
        }
      />
      <Modal animationType="fade" transparent visible={isCreateModalVisible}>
        <View style={styles.modalBackdrop}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>新建学习库</Text>
            <TextInput
              placeholder="例如：计算机网络"
              style={styles.input}
              value={draftName}
              onChangeText={setDraftName}
            />
            <Pressable style={styles.saveButton} onPress={() => void createNewLibrary()}>
              <Text style={styles.saveButtonText}>保存</Text>
            </Pressable>
            <Pressable onPress={() => {
              setDraftName('');
              setIsCreateModalVisible(false);
            }}>
              <Text style={styles.cancelText}>取消</Text>
            </Pressable>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { backgroundColor: '#F8FAFC', flex: 1, paddingHorizontal: 20, paddingTop: 64 },
  header: { marginBottom: 18 },
  title: { color: '#0F172A', fontSize: 26, fontWeight: '700' },
  subtitle: { color: '#64748B', fontSize: 14, lineHeight: 21, marginTop: 8 },
  libraryCard: { alignItems: 'center', backgroundColor: '#FFFFFF', borderColor: '#E2E8F0', borderRadius: 12, borderWidth: 1, flexDirection: 'row', marginBottom: 10, padding: 16 },
  iconBox: { alignItems: 'center', backgroundColor: '#DBEAFE', borderRadius: 10, height: 42, justifyContent: 'center', width: 42 },
  iconText: { color: '#2563EB', fontSize: 16, fontWeight: '700' },
  libraryInfo: { marginLeft: 12 },
  libraryName: { color: '#0F172A', fontSize: 16, fontWeight: '700' },
  libraryMeta: { color: '#64748B', fontSize: 13, marginTop: 5 },
  createButton: { alignItems: 'center', borderColor: '#2563EB', borderRadius: 10, borderWidth: 1, marginTop: 10, paddingVertical: 14 },
  createButtonText: { color: '#2563EB', fontSize: 15, fontWeight: '700' },
  emptyText: { color: '#64748B', marginTop: 20, textAlign: 'center' },
  modalBackdrop: { flex: 1, justifyContent: 'center', padding: 20 },
  modalCard: { backgroundColor: '#FFFFFF', borderRadius: 16, padding: 20 },
  modalTitle: { color: '#0F172A', fontSize: 18, fontWeight: '700' },
  input: { borderColor: '#CBD5E1', borderRadius: 8, borderWidth: 1, marginTop: 16, padding: 12 },
  saveButton: { alignItems: 'center', backgroundColor: '#2563EB', borderRadius: 8, marginTop: 12, padding: 12 },
  saveButtonText: { color: '#FFFFFF', fontWeight: '700' },
  cancelText: { color: '#64748B', marginTop: 14, textAlign: 'center' },
});
