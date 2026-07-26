import { router, useLocalSearchParams } from 'expo-router';
import { Alert, Pressable, StyleSheet, Text, View } from 'react-native';
import { initialLibraries } from '../../data/libraries';

export default function LibraryDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const selectedLibrary = initialLibraries.find((library) => library.id === id);

  function showNextStageMessage(featureName: string) {
    Alert.alert(featureName, '这个功能会在后续阶段实现。');
  }

  if (!selectedLibrary) {
    return null;
  }

  return (
    <View style={styles.container}>
      <Pressable style={styles.backButton} onPress={() => router.back()}>
        <Text style={styles.backButtonText}>‹ 返回</Text>
      </Pressable>
      <Text style={styles.title}>{selectedLibrary.name}</Text>
      <Text style={styles.meta}>共 {selectedLibrary.questionCount} 题</Text>
      <Text style={styles.meta}>{selectedLibrary.wrongQuestionCount} 道错题</Text>
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
});
