import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './auth/ProtectedRoute';
import AppLayout from './layouts/AppLayout';
import AttendancePage from './pages/AttendancePage';
import ContractPage from './pages/ContractPage';
import DepartmentPage from './pages/DepartmentPage';
import EmployeePage from './pages/EmployeePage';
import EmployeeProfile from './pages/EmployeeProfile';
import HolidayPage from './pages/HolidayPage';
import LeaveQuotaPage from './pages/LeaveQuotaPage';
import MyLeavePage from './pages/MyLeavePage';
import OrgChartPage from './pages/OrgChartPage';
import WorkShiftPage from './pages/WorkShiftPage';

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
          <Route index element={<Navigate to="/employees" replace />} />
          <Route
            path="employees"
            element={
              <ProtectedRoute roles={STAFF_ROLES}>
                <EmployeePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="employees/:id"
            element={
              <ProtectedRoute roles={STAFF_ROLES}>
                <EmployeeProfile />
              </ProtectedRoute>
            }
          />
          <Route
            path="departments"
            element={
              <ProtectedRoute roles={STAFF_ROLES}>
                <DepartmentPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="contracts"
            element={
              <ProtectedRoute roles={STAFF_ROLES}>
                <ContractPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="org-chart"
            element={
              <ProtectedRoute roles={STAFF_ROLES}>
                <OrgChartPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="attendance"
            element={
              <ProtectedRoute roles={STAFF_ROLES}>
                <AttendancePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="holidays"
            element={
              <ProtectedRoute roles={STAFF_ROLES}>
                <HolidayPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="leave-quota"
            element={
              <ProtectedRoute roles={STAFF_ROLES}>
                <LeaveQuotaPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="work-shifts"
            element={
              <ProtectedRoute roles={STAFF_ROLES}>
                <WorkShiftPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="my-leave"
            element={
              <ProtectedRoute>
                <MyLeavePage />
              </ProtectedRoute>
            }
          />
          {/* redirect URL cũ (tiếng Việt không dấu) */}
          <Route path="nhan-su" element={<Navigate to="/employees" replace />} />
          <Route path="nhan-su/:id" element={<Navigate to="/employees" replace />} />
          <Route path="phong-ban" element={<Navigate to="/departments" replace />} />
          <Route path="hop-dong" element={<Navigate to="/contracts" replace />} />
          <Route path="to-chuc" element={<Navigate to="/org-chart" replace />} />
          <Route path="cham-cong" element={<Navigate to="/attendance" replace />} />
          <Route path="ngay-le" element={<Navigate to="/holidays" replace />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
