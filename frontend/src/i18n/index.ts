import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import attendanceVi from './locales/vi/attendance.json';
import authVi from './locales/vi/auth.json';
import commonVi from './locales/vi/common.json';
import contractVi from './locales/vi/contract.json';
import departmentVi from './locales/vi/department.json';
import employeeVi from './locales/vi/employee.json';
import holidayVi from './locales/vi/holiday.json';
import layoutVi from './locales/vi/layout.json';
import leaveVi from './locales/vi/leave.json';
import orgchartVi from './locales/vi/orgchart.json';
import profileVi from './locales/vi/profile.json';
import shiftVi from './locales/vi/shift.json';
import attendanceEn from './locales/en/attendance.json';
import authEn from './locales/en/auth.json';
import commonEn from './locales/en/common.json';
import contractEn from './locales/en/contract.json';
import departmentEn from './locales/en/department.json';
import employeeEn from './locales/en/employee.json';
import holidayEn from './locales/en/holiday.json';
import layoutEn from './locales/en/layout.json';
import leaveEn from './locales/en/leave.json';
import orgchartEn from './locales/en/orgchart.json';
import profileEn from './locales/en/profile.json';
import shiftEn from './locales/en/shift.json';

/**
 * Bộ translation: common/layout/auth merge phẳng (key dùng chung: save, menu.*, status.*),
 * các file theo màn hình giữ namespace root (employee.*, profile.*, contract.*, ...).
 */
const vi = {
  ...commonVi,
  ...layoutVi,
  ...authVi,
  employee: employeeVi,
  holiday: holidayVi,
  leave: leaveVi,
  profile: profileVi,
  contract: contractVi,
  department: departmentVi,
  attendance: attendanceVi,
  orgchart: orgchartVi,
  shift: shiftVi,
};

const en = {
  ...commonEn,
  ...layoutEn,
  ...authEn,
  employee: employeeEn,
  holiday: holidayEn,
  leave: leaveEn,
  profile: profileEn,
  contract: contractEn,
  department: departmentEn,
  attendance: attendanceEn,
  orgchart: orgchartEn,
  shift: shiftEn,
};

export const LANGUAGES: Record<string, string> = {
  vi: 'Tiếng Việt',
  en: 'English',
};

export type Language = 'vi' | 'en';

const STORAGE_KEY = 'hrms.lang';

function initialLanguage(): Language {
  const saved = localStorage.getItem(STORAGE_KEY);
  return saved === 'en' ? 'en' : 'vi';
}

i18n.use(initReactI18next).init({
  resources: {
    vi: { translation: vi },
    en: { translation: en },
  },
  lng: initialLanguage(),
  fallbackLng: 'vi',
  interpolation: { escapeValue: false },
});

export function setLanguage(lang: Language) {
  localStorage.setItem(STORAGE_KEY, lang);
  void i18n.changeLanguage(lang);
}

export default i18n;
