export type Library = {
  id: string;
  name: string;
  wrongQuestionCount: number;
  createdAt: string;
  updatedAt: string;
};

type LibraryResponse = Omit<Library, 'id' | 'wrongQuestionCount'> & { id: number };

const API_BASE_URL = 'http://192.168.10.6:8080';

export async function fetchLibraries(): Promise<Library[]> {
  const response = await fetch(`${API_BASE_URL}/api/libraries`);

  if (!response.ok) {
    throw new Error('学习库加载失败');
  }

  const libraries: LibraryResponse[] = await response.json();

  return libraries.map(normalizeLibrary);
}

export async function createLibrary(name: string): Promise<Library> {
  const response = await fetch(`${API_BASE_URL}/api/libraries`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  });

  if (!response.ok) {
    throw new Error('学习库创建失败');
  }

  return normalizeLibrary(await response.json());
}

export async function updateLibrary(id: string, name: string): Promise<Library> {
  const response = await fetch(`${API_BASE_URL}/api/libraries/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  });

  if (!response.ok) {
    throw new Error('学习库修改失败');
  }

  return normalizeLibrary(await response.json());
}

export async function deleteLibrary(id: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/libraries/${id}`, {
    method: 'DELETE',
  });

  if (!response.ok) {
    throw new Error('学习库删除失败');
  }
}

function normalizeLibrary(library: LibraryResponse): Library {
  return {
    ...library,
    id: String(library.id),
    wrongQuestionCount: 0,
  };
}
