import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './auth/ProtectedRoute';
import AppLayout from './layouts/AppLayout';
import AttendancePage from './pages/AttendancePage';
import EmployeePage from './pages/EmployeePage';

const STAFF_ROLES = ['HR_ADMIN', 'MANAGER'];

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <AppLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<Navigate to="/nhan-su" replace />} />
          <Route
            path="nhan-su"
            element={
              <ProtectedRoute roles={STAFF_ROLES}>
                <EmployeePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="cham-cong"
            element={
              <ProtectedRoute roles={STAFF_ROLES}>
                <AttendancePage />
              </ProtectedRoute>
            }
          />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
