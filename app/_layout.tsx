import { Stack } from 'expo-router';
import { AuthProvider } from '../contexts/AuthContext';
import { LibraryProvider } from '../contexts/LibraryContext';

export default function RootLayout() {
  return (
    <AuthProvider>
      <LibraryProvider>
        <Stack screenOptions={{ headerShown: false }} />
      </LibraryProvider>
    </AuthProvider>
  );
}
