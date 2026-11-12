import { lazy, Suspense } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './auth/ProtectedRoute';
import AppLayout from './layouts/AppLayout';

const AttendancePage = lazy(() => import('./pages/AttendancePage'));
const ContractPage = lazy(() => import('./pages/ContractPage'));
const DepartmentPage = lazy(() => import('./pages/DepartmentPage'));
const EmployeePage = lazy(() => import('./pages/EmployeePage'));
const EmployeeProfile = lazy(() => import('./pages/EmployeeProfile'));
const HolidayPage = lazy(() => import('./pages/HolidayPage'));
const LeaveQuotaPage = lazy(() => import('./pages/LeaveQuotaPage'));
const MyLeavePage = lazy(() => import('./pages/MyLeavePage'));
const MyProfilePage = lazy(() => import('./pages/MyProfilePage'));
const OrgChartPage = lazy(() => import('./pages/OrgChartPage'));
const SettingsAccountsPage = lazy(() => import('./pages/SettingsAccountsPage'));
const SettingsPoliciesPage = lazy(() => import('./pages/SettingsPoliciesPage'));
const SettingsSecurityPage = lazy(() => import('./pages/SettingsSecurityPage'));
const SchedulePage = lazy(() => import('./pages/SchedulePage'));
const WorkShiftPage = lazy(() => import('./pages/WorkShiftPage'));
const OTManagementPage = lazy(() => import('./pages/OTManagementPage'));
const AuditLogPage = lazy(() => import('./pages/AuditLogPage'));

const STAFF_ROLES = ['ADMIN', 'GLOBAL_LEAD', 'DEPT_LEAD', 'MANAGER'];
const OT_ROLES = [...STAFF_ROLES, 'EMPLOYEE'];

export default function App() {
  return (
    <BrowserRouter>
      <Suspense fallback={<div style={{ padding: 24 }}>Loading...</div>}>
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
              path="schedule"
              element={
                <ProtectedRoute roles={STAFF_ROLES}>
                  <SchedulePage />
                </ProtectedRoute>
              }
            />
            <Route
              path="ot"
              element={
                <ProtectedRoute roles={OT_ROLES}>
                  <OTManagementPage />
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
            <Route
              path="my-profile"
              element={
                <ProtectedRoute>
                  <MyProfilePage />
                </ProtectedRoute>
              }
            />
            <Route
              path="settings/accounts"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <SettingsAccountsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="settings/policies"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <SettingsPoliciesPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="settings/security"
              element={
                <ProtectedRoute>
                  <SettingsSecurityPage />
                </ProtectedRoute>
              }
            />
          <Route
            path="audit-logs"
            element={
              <ProtectedRoute roles={['ADMIN']}>
                <AuditLogPage />
              </ProtectedRoute>
            }
          />
            <Route path="settings" element={<Navigate to="/settings/security" replace />} />
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
      </Suspense>
    </BrowserRouter>
  );
}