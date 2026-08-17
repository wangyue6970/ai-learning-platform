import { Stack } from 'expo-router';
import { DialogProvider } from '../components/AppDialog';
import { AuthProvider } from '../contexts/AuthContext';
import { LibraryProvider } from '../contexts/LibraryContext';

export default function RootLayout() {
  return (
    <AuthProvider>
      <DialogProvider>
        <LibraryProvider>
          <Stack screenOptions={{ headerShown: false }} />
        </LibraryProvider>
      </DialogProvider>
    </AuthProvider>
  );
}
