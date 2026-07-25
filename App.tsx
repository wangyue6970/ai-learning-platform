import { StatusBar } from 'expo-status-bar';
import { useState } from 'react';
import { FlatList, Pressable, StyleSheet, Text, View } from 'react-native';

type Library = {
  id: string;
  name: string;
  questionCount: number;
  wrongQuestionCount: number;
};

const initialLibraries: Library[] = [
  {
    id: 'operating-systems',
    name: '操作系统期末',
    questionCount: 128,
    wrongQuestionCount: 12,
  },
  {
    id: 'data-structures',
    name: '数据结构',
    questionCount: 86,
    wrongQuestionCount: 5,
  },
  {
    id: 'english-vocabulary',
    name: '英语四级词汇',
    questionCount: 245,
    wrongQuestionCount: 0,
  },
];

export default function App() {
  const [libraries, setLibraries] = useState<Library[]>(initialLibraries);

  function createLibrary() {
    setLibraries((currentLibraries) => [
      ...currentLibraries,
      {
        id: `local-library-${Date.now()}`,
        name: `新学习库 ${currentLibraries.length + 1}`,
        questionCount: 0,
        wrongQuestionCount: 0,
      },
    ]);
  }

  return (
    <View style={styles.container}>
      <StatusBar style="dark" />
      <FlatList
        data={libraries}
        keyExtractor={(library) => library.id}
        renderItem={({ item }) => (
          <View style={styles.libraryCard}>
            <View style={styles.iconBox}>
              <Text style={styles.iconText}>库</Text>
            </View>
            <View style={styles.libraryInfo}>
              <Text style={styles.libraryName}>{item.name}</Text>
              <Text style={styles.libraryMeta}>共 {item.questionCount} 题 · {item.wrongQuestionCount} 道错题</Text>
            </View>
          </View>
        )}
        ListHeaderComponent={
          <View style={styles.header}>
            <Text style={styles.title}>我的学习库</Text>
            <Text style={styles.subtitle}>题目、错题和练习记录都会归属于一个学习库。</Text>
          </View>
        }
        ListFooterComponent={
          <Pressable style={styles.createButton} onPress={createLibrary}>
            <Text style={styles.createButtonText}>＋ 新建学习库</Text>
          </Pressable>
        }
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#F8FAFC',
    flex: 1,
    paddingHorizontal: 20,
    paddingTop: 64,
  },
  header: {
    marginBottom: 18,
  },
  title: {
    color: '#0F172A',
    fontSize: 26,
    fontWeight: '700',
  },
  subtitle: {
    color: '#64748B',
    fontSize: 14,
    lineHeight: 21,
    marginTop: 8,
  },
  libraryCard: {
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderColor: '#E2E8F0',
    borderRadius: 12,
    borderWidth: 1,
    flexDirection: 'row',
    marginBottom: 10,
    padding: 16,
  },
  iconBox: {
    alignItems: 'center',
    backgroundColor: '#DBEAFE',
    borderRadius: 10,
    height: 42,
    justifyContent: 'center',
    width: 42,
  },
  iconText: {
    color: '#2563EB',
    fontSize: 16,
    fontWeight: '700',
  },
  libraryInfo: {
    marginLeft: 12,
  },
  libraryName: {
    color: '#0F172A',
    fontSize: 16,
    fontWeight: '700',
  },
  libraryMeta: {
    color: '#64748B',
    fontSize: 13,
    marginTop: 5,
  },
  createButton: {
    alignItems: 'center',
    borderColor: '#2563EB',
    borderRadius: 10,
    borderWidth: 1,
    marginTop: 10,
    paddingVertical: 14,
  },
  createButtonText: {
    color: '#2563EB',
    fontSize: 15,
    fontWeight: '700',
  },
});
