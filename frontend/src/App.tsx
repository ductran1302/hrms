import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './auth/ProtectedRoute';
import AppLayout from './layouts/AppLayout';
import AttendancePage from './pages/AttendancePage';
import EmployeePage from './pages/EmployeePage';
import EmployeeProfile from './pages/EmployeeProfile';
import HopDongPage from './pages/HopDongPage';
import LePage from './pages/LePage';
import OrgChartPage from './pages/OrgChartPage';
import PhongBanPage from './pages/PhongBanPage';

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
            path="nhan-su/:id"
            element={
              <ProtectedRoute roles={STAFF_ROLES}>
                <EmployeeProfile />
              </ProtectedRoute>
            }
          />
          <Route
            path="phong-ban"
            element={
              <ProtectedRoute roles={STAFF_ROLES}>
                <PhongBanPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="hop-dong"
            element={
              <ProtectedRoute roles={STAFF_ROLES}>
                <HopDongPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="to-chuc"
            element={
              <ProtectedRoute roles={STAFF_ROLES}>
                <OrgChartPage />
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
          <Route
            path="ngay-le"
            element={
              <ProtectedRoute roles={STAFF_ROLES}>
                <LePage />
              </ProtectedRoute>
            }
          />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
