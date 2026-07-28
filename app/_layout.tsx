import { Stack } from 'expo-router';
import { LibraryProvider } from '../contexts/LibraryContext';
import { QuestionLearningProvider } from '../contexts/QuestionLearningContext';

export default function RootLayout() {
  return (
    <LibraryProvider>
      <QuestionLearningProvider>
        <Stack screenOptions={{ headerShown: false }} />
      </QuestionLearningProvider>
    </LibraryProvider>
  );
}
