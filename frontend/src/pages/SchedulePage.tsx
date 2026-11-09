import {
  CalendarOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  ToolOutlined,
} from '@ant-design/icons';
import {
  App as AntApp,
  Button,
  DatePicker,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { listAttendanceEmployees } from '../api/attendance';
import { getLeaveSettings } from '../api/leaveQuotas';
import { errorMessage } from '../api/client';
import { searchEmployees } from '../api/employees';
import {
  clearRosterOverride,
  createScheduleAssignment,
  createSchedulePolicy,
  deleteScheduleAssignment,
  deleteSchedulePolicy,
  getRosterDay,
  listRosters,
  listScheduleAssignments,
  listSchedulePolicies,
  overrideRoster,
  updateSchedulePolicy,
} from '../api/schedules';
import type {
  AttendanceEmployee,
  LeaveSettings,
  RosterEntry,
  RosterSourceValue,
  ScheduleAssignment,
  WorkSchedulePolicy,
  WorkShift,
} from '../api/types';
import { listWorkShifts } from '../api/workShifts';
import { useAuth } from '../auth/AuthContext';
import ActionIcon from '../components/ActionIcon';

const { Text } = Typography;

const SOURCE_COLOR: Record<RosterSourceValue, string> = {
  POLICY_AUTO: 'default',
  ROTATION_GEN: 'cyan',
  MANUAL_OVERRIDE: 'orange',
  LEGACY_ASSIGNMENT: 'purple',
};

const POL_FIELDS = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'] as const;

/** Select dùng '' = nghỉ (null không hợp lệ với antd Select). */
const OFF = '';

interface CellFormValues {
  shiftId: string;
  note?: string;
}

interface PolFormValues {
  name: string;
  description?: string;
  mon?: string;
  tue?: string;
  wed?: string;
  thu?: string;
  fri?: string;
  sat?: string;
  sun?: string;
  rotation?: boolean;
  alternate?: string;
  anchor?: Dayjs;
  defaultPolicy?: boolean;
  active?: boolean;
}

interface AsgFormValues {
  employeeId: string;
  policyId: string;
  effectiveFrom: Dayjs;
}

const cellKey = (employeeId: string, date: string) => `${employeeId}|${date}`;
const hm = (time: string | null | undefined) => (time ? time.slice(0, 5) : null);

/** Backend chỉ cho xem tối đa 62 ngày/lần (giới hạn tải lịch suy ra). */
const MAX_RANGE_DAYS = 62;

/** Kỳ công hiện tại theo leave settings (periodStartDay/periodEndDay). */
function currentWorkPeriod(settings: LeaveSettings | null): [Dayjs, Dayjs] {
  const today = dayjs();
  const startDay = settings?.periodStartDay ?? 1;
  const endDay = settings?.periodEndDay ?? 31;
  if (startDay === 1) {
    return [today.startOf('month'), today.endOf('month').date(Math.min(endDay, today.daysInMonth()))];
  }
  if (today.date() >= startDay) {
    const next = today.add(1, 'month');
    return [today.startOf('month').date(startDay), next.startOf('month').date(Math.min(endDay, next.daysInMonth()))];
  }
  const prev = today.subtract(1, 'month');
  return [prev.startOf('month').date(startDay), today.startOf('month').date(Math.min(endDay, today.daysInMonth()))];
}
export default function SchedulePage() {
  const { t } = useTranslation();
  const auth = useAuth();
  const canWrite = auth.hasRole('ADMIN');
  const { message, modal } = AntApp.useApp();

  const [tab, setTab] = useState('calendar');

  // ===== Lịch =====
  const [range, setRange] = useState<[Dayjs, Dayjs]>(currentWorkPeriod(null));
  const [empFilter, setEmpFilter] = useState<string | undefined>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(50);
  const [employees, setEmployees] = useState<AttendanceEmployee[]>([]);
  const [shifts, setShifts] = useState<WorkShift[]>([]);
  const [rosters, setRosters] = useState<Map<string, RosterEntry>>(new Map());
  const [calLoading, setCalLoading] = useState(false);

  useEffect(() => {
    getLeaveSettings()
      .then((s) => {
        setRange(currentWorkPeriod(s));
      })
      .catch(() => {});
  }, []);

  const [cell, setCell] = useState<{
    emp: AttendanceEmployee;
    date: Dayjs;
    entry: RosterEntry | null;
  } | null>(null);
  const [cellForm] = Form.useForm<CellFormValues>();
  const [cellSaving, setCellSaving] = useState(false);

  // ===== Chính sách =====
  const [policies, setPolicies] = useState<WorkSchedulePolicy[]>([]);
  const [polLoading, setPolLoading] = useState(false);
  const [polModalOpen, setPolModalOpen] = useState(false);
  const [editingPol, setEditingPol] = useState<WorkSchedulePolicy | null>(null);
  const [polForm] = Form.useForm<PolFormValues>();
  const rotation = Form.useWatch('rotation', polForm);

  // ===== Phân công =====
  const [assignments, setAssignments] = useState<ScheduleAssignment[]>([]);
  const [asgLoading, setAsgLoading] = useState(false);
  const [asgOpen, setAsgOpen] = useState(false);
  const [asgForm] = Form.useForm<AsgFormValues>();
  const [empOptions, setEmpOptions] = useState<{ value: string; label: string }[]>([]);

  const todayStart = dayjs().startOf('day');

  const loadStatic = useCallback(async () => {
    try {
      const [emps, shs] = await Promise.all([listAttendanceEmployees(), listWorkShifts()]);
      setEmployees(emps);
      setShifts(shs);
    } catch (e) {
      message.error(errorMessage(e));
    }
  }, [message]);

  const loadRosters = useCallback(async () => {
    setCalLoading(true);
    try {
      const active = employees.filter(
        (e) => e.status !== 'TERMINATED' && (!empFilter || e.employeeId === empFilter)
      );
      const ids = active
        .slice((page - 1) * pageSize, page * pageSize)
        .map((e) => e.employeeId);
      if (ids.length === 0) {
        setRosters(new Map());
        return;
      }
      const list = await listRosters({
        from: range[0].format('YYYY-MM-DD'),
        to: range[1].format('YYYY-MM-DD'),
        employeeIds: ids,
      });
      const m = new Map<string, RosterEntry>();
      list.forEach((e) => m.set(cellKey(e.employeeId, e.workDate), e));
      setRosters(m);
    } catch (e) {
      message.error(errorMessage(e));
    } finally {
      setCalLoading(false);
    }
  }, [range, empFilter, page, pageSize, employees, message]);

  const loadPolicies = useCallback(async () => {
    setPolLoading(true);
    try {
      setPolicies(await listSchedulePolicies());
    } catch (e) {
      message.error(errorMessage(e));
    } finally {
      setPolLoading(false);
    }
  }, [message]);

  const loadAssignments = useCallback(async () => {
    setAsgLoading(true);
    try {
      setAssignments(await listScheduleAssignments());
    } catch (e) {
      message.error(errorMessage(e));
    } finally {
      setAsgLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void loadStatic();
  }, [loadStatic]);
  useEffect(() => {
    void loadRosters();
  }, [loadRosters]);
  useEffect(() => {
    void loadPolicies();
    void loadAssignments();
  }, [loadPolicies, loadAssignments]);

  useEffect(() => {
    setPage(1);
  }, [empFilter, range]);

  const days = useMemo(() => {
    const out: Dayjs[] = [];
    let d = range[0].startOf('day');
    const end = range[1];
    while (!d.isAfter(end, 'day') && out.length < 400) {
      out.push(d);
      d = d.add(1, 'day');
    }
    return out;
  }, [range]);

  const visibleEmployees = useMemo(
    () =>
      employees.filter(
        (e) => e.status !== 'TERMINATED' && (!empFilter || e.employeeId === empFilter)
      ),
    [employees, empFilter]
  );

  // ===== Cell modal =====
  const openCell = async (emp: AttendanceEmployee, date: Dayjs) => {
    const dateStr = date.format('YYYY-MM-DD');
    let entry = rosters.get(cellKey(emp.employeeId, dateStr)) ?? null;
    setCell({ emp, date, entry });
    cellForm.resetFields();
    if (!entry) {
      try {
        entry = await getRosterDay(emp.employeeId, dateStr);
        setRosters((prev) => new Map(prev).set(cellKey(entry!.employeeId, entry!.workDate), entry!));
        setCell({ emp, date, entry });
      } catch (e) {
        message.error(errorMessage(e));
        setCell(null);
        return;
      }
    }
    cellForm.setFieldsValue({
      shiftId: entry && !entry.dayOff && entry.shiftId ? entry.shiftId : OFF,
      note: entry?.note ?? undefined,
    });
  };

  const editableCell = cell && cell.date.isAfter(todayStart.subtract(1, 'day'), 'day');

  const saveCell = async () => {
    if (!cell) return;
    const v = await cellForm.validateFields();
    setCellSaving(true);
    try {
      const updated = await overrideRoster(cell.emp.employeeId, cell.date.format('YYYY-MM-DD'), {
        shiftId: v.shiftId === OFF ? null : v.shiftId,
        note: v.note?.trim() || null,
      });
      setRosters((prev) => new Map(prev).set(cellKey(updated.employeeId, updated.workDate), updated));
      setCell({ ...cell, entry: updated });
      message.success(
        t('schedule.cell.overrideSuccess', { date: cell.date.format('DD/MM/YYYY') })
      );
    } catch (e) {
      message.error(errorMessage(e));
    } finally {
      setCellSaving(false);
    }
  };

  const clearCell = async () => {
    if (!cell) return;
    try {
      const updated = await clearRosterOverride(
        cell.emp.employeeId,
        cell.date.format('YYYY-MM-DD')
      );
      setRosters((prev) => new Map(prev).set(cellKey(updated.employeeId, updated.workDate), updated));
      setCell({ ...cell, entry: updated });
      cellForm.setFieldsValue({
        shiftId: updated && !updated.dayOff && updated.shiftId ? updated.shiftId : OFF,
        note: updated.note ?? undefined,
      });
      message.success(
        t('schedule.cell.clearSuccess', { date: cell.date.format('DD/MM/YYYY') })
      );
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  // ===== Chính sách =====
  const openCreatePolicy = () => {
    setEditingPol(null);
    polForm.resetFields();
    polForm.setFieldsValue({
      mon: OFF,
      tue: OFF,
      wed: OFF,
      thu: OFF,
      fri: OFF,
      sat: OFF,
      sun: OFF,
      rotation: false,
      defaultPolicy: false,
      active: true,
    });
    setPolModalOpen(true);
  };

  const openEditPolicy = (p: WorkSchedulePolicy) => {
    setEditingPol(p);
    polForm.resetFields();
    polForm.setFieldsValue({
      name: p.name,
      description: p.description ?? undefined,
      mon: p.monShiftId ?? OFF,
      tue: p.tueShiftId ?? OFF,
      wed: p.wedShiftId ?? OFF,
      thu: p.thuShiftId ?? OFF,
      fri: p.friShiftId ?? OFF,
      sat: p.satShiftId ?? OFF,
      sun: p.sunShiftId ?? OFF,
      rotation: !!p.satAlternateShiftId,
      alternate: p.satAlternateShiftId ?? undefined,
      anchor: p.rotationAnchorDate ? dayjs(p.rotationAnchorDate) : undefined,
      defaultPolicy: p.defaultPolicy,
      active: p.active,
    });
    setPolModalOpen(true);
  };

  const submitPolicy = async () => {
    const v = await polForm.validateFields();
    const doSave = async () => {
      try {
        const body = {
          name: v.name,
          description: v.description?.trim() || null,
          monShiftId: v.mon || null,
          tueShiftId: v.tue || null,
          wedShiftId: v.wed || null,
          thuShiftId: v.thu || null,
          friShiftId: v.fri || null,
          satShiftId: v.sat || null,
          satAlternateShiftId: v.rotation ? v.alternate || null : null,
          rotationAnchorDate: v.rotation && v.anchor ? v.anchor.format('YYYY-MM-DD') : null,
          defaultPolicy: !!v.defaultPolicy,
          active: v.active !== false,
        };
        if (editingPol) {
          await updateSchedulePolicy(editingPol.id, body);
        } else {
          await createSchedulePolicy(body);
        }
        message.success(t('saved'));
        setPolModalOpen(false);
        await loadPolicies();
        await loadRosters();
      } catch (e) {
        message.error(errorMessage(e));
      }
    };
    if (v.defaultPolicy && !editingPol?.defaultPolicy) {
      modal.confirm({ title: t('schedule.policy.form.defaultConfirm'), onOk: doSave });
    } else {
      await doSave();
    }
  };

  const removePolicy = async (id: string) => {
    try {
      await deleteSchedulePolicy(id);
      message.success(t('deleted'));
      await loadPolicies();
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  /** Options ca cho ô select � giá trị hiện tại không còn active vẫn giữ label. */
  const shiftSelectOptions = (current?: string | null) => {
    const opts = shifts
      .filter((s) => s.active)
      .map((s) => ({
        value: s.id,
        label: `${s.name} (${hm(s.startWorkTime)}-${hm(s.endWorkTime)})${
          s.crossesMidnight ? ` ${t('shift.column.nextDay')}` : ''
        }`,
      }));
    if (current && !opts.some((o) => o.value === current)) {
      const s = shifts.find((x) => x.id === current);
      opts.unshift({ value: current, label: s ? s.name : current });
    }
    return opts;
  };

  // ===== Phân công =====
  const searchEmps = async (keyword: string) => {
    try {
      const page = await searchEmployees({ q: keyword || undefined, size: 20 });
      setEmpOptions(
        page.items.map((e) => ({ value: e.id, label: `${e.employeeCode} — ${e.fullName}` }))
      );
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const submitAssign = async () => {
    const v = await asgForm.validateFields();
    try {
      await createScheduleAssignment({
        employeeId: v.employeeId,
        policyId: v.policyId,
        effectiveFrom: v.effectiveFrom.format('YYYY-MM-DD'),
      });
      message.success(t('saved'));
      setAsgOpen(false);
      await loadAssignments();
      await loadRosters();
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const removeAssign = async (id: string) => {
    try {
      await deleteScheduleAssignment(id);
      message.success(t('deleted'));
      await loadAssignments();
      await loadRosters();
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  // ===== Cột lịch =====
  const renderCell = (emp: AttendanceEmployee, d: Dayjs) => {
    const dateStr = d.format('YYYY-MM-DD');
    const entry = rosters.get(cellKey(emp.employeeId, dateStr));
    const manual = entry?.source === 'MANUAL_OVERRIDE';
    let content: ReactNode;
    if (!entry) {
      content = (
        <Tooltip title={t('schedule.calendar.noDataHint')}>
          <span style={{ color: '#bfbfbf' }}>{t('schedule.calendar.noData')}</span>
        </Tooltip>
      );
    } else if (entry.dayOff) {
      content = (
        <Tag color={manual ? 'orange' : 'default'} style={{ marginInlineEnd: 0 }}>
          {t('schedule.calendar.dayOff')}
        </Tag>
      );
    } else {
      const color = manual ? 'orange' : entry.shiftType === 'FIXED' ? 'blue' : 'geekblue';
      content = (
        <Tooltip title={`${entry.shiftName ?? ''}${entry.note ? ` — ${entry.note}` : ''}`}>
          <Tag
            color={color}
            style={{
              marginInlineEnd: 0,
              maxWidth: 72,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              display: 'inline-block',
              verticalAlign: 'bottom',
            }}
          >
            {entry.shiftName ?? t('schedule.calendar.dayOff')}
            {entry.crossesMidnight ? '+1' : ''}
            {manual ? ' ✎' : ''}
          </Tag>
        </Tooltip>
      );
    }
    return (
      <div
        style={{
          textAlign: 'center',
          cursor: 'pointer',
          minHeight: 26,
        }}
        onClick={() => void openCell(emp, d)}
      >
        {content}
      </div>
    );
  };

  const calColumns = useMemo(() => {
    const first = {
      title: t('schedule.calendar.employeeColumn'),
      dataIndex: 'employeeCode',
      key: 'employee',
      fixed: 'left' as const,
      width: 170,
      render: (_: unknown, e: AttendanceEmployee) => (
        <div>
          <Text strong>{e.employeeCode}</Text>
          <div style={{ fontSize: 12, color: '#8c8c8c', lineHeight: 1.2 }}>{e.fullName}</div>
        </div>
      ),
    };
    const dayCols = days.map((d) => ({
      title: (
        <div style={{ textAlign: 'center', lineHeight: 1.15 }}>
          <div
            style={{
              fontSize: 10,
              color: d.day() === 0 || d.day() === 6 ? '#ff4d4f' : '#8c8c8c',
            }}
          >
            {t(`schedule.weekday.${d.day()}`)}
          </div>
          <div style={{ fontWeight: 600 }}>{d.format('DD/MM')}</div>
        </div>
      ),
      key: d.format('YYYY-MM-DD'),
      width: 78,
      align: 'center' as const,
      render: (_: unknown, e: AttendanceEmployee) => renderCell(e, d),
    }));
    return [first, ...dayCols];
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [days, rosters, t, shifts, cell]);

  // ===== Cột chính sách =====
  const policyColumns: ColumnsType<WorkSchedulePolicy> = [
    {
      title: t('schedule.policy.column.name'),
      dataIndex: 'name',
      width: 180,
      ellipsis: true,
      render: (v: string, p) => (
        <Space size={4}>
          <Text strong>{v}</Text>
          {p.defaultPolicy && <Tag color="blue">{t('schedule.policy.column.default')}</Tag>}
        </Space>
      ),
    },
    {
      title: t('schedule.policy.column.description'),
      dataIndex: 'description',
      width: 170,
      ellipsis: true,
      render: (v: string | null) => v ?? '—',
    },
    {
      title: t('schedule.policy.column.week'),
      key: 'week',
      width: 420,
      render: (_, p) => (
        <Space size={2} wrap>
          {POL_FIELDS.map((f) => {
            const id = p[`${f}ShiftId` as keyof WorkSchedulePolicy] as string | null;
            const name = id ? (p.shiftNames[id] ?? t('schedule.calendar.dayOff')) : '—';
            return (
              <Tooltip key={f} title={`${t(`schedule.policy.day.${f}`)}: ${id ? name : '—'}`}>
                <Tag
                  color={id ? 'blue' : 'default'}
                  style={{
                    marginInlineEnd: 0,
                    maxWidth: 56,
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                    display: 'inline-block',
                    verticalAlign: 'bottom',
                  }}
                >
                  {t(`schedule.policy.day.${f}`)}: {name}
                  {f === 'sat' && p.satAlternateShiftId ? ' ⇄' : ''}
                </Tag>
              </Tooltip>
            );
          })}
        </Space>
      ),
    },
    {
      title: t('schedule.policy.column.active'),
      dataIndex: 'active',
      width: 90,
      render: (v: boolean) =>
        v ? (
          <Tag color="green">{t('shift.status.active')}</Tag>
        ) : (
          <Tag>{t('shift.status.inactive')}</Tag>
        ),
    },
    {
      title: t('schedule.assignment.form.effectiveFrom'),
      dataIndex: 'effectiveFrom',
      width: 110,
      render: (v: string | null) => (v ? dayjs(v).format('DD/MM/YYYY') : '-'),
    },
    {
      title: t('schedule.policy.column.effectiveTo', { defaultValue: 'Hiệu lực đến' }),
      dataIndex: 'effectiveTo',
      width: 110,
      render: (v: string | null) => (v ? dayjs(v).format('DD/MM/YYYY') : '-'),
    },
    {
      title: t('schedule.policy.column.updated'),
      dataIndex: 'updatedAt',
      width: 110,
      render: (v: string) => (v ? dayjs(v).format('DD/MM/YYYY') : '—'),
    },
    {
      title: t('actions'),
      width: 90,
      render: (_, p) =>
        canWrite ? (
          <Space>
            <ActionIcon title={t('schedule.policy.edit')} color="#1677ff" onClick={() => openEditPolicy(p)}>
              <EditOutlined />
            </ActionIcon>
            <Popconfirm
              title={t('schedule.policy.confirmDelete')}
              onConfirm={() => void removePolicy(p.id)}
            >
              <ActionIcon title={t('delete')} color="#ff4d4f">
                <DeleteOutlined />
              </ActionIcon>
            </Popconfirm>
          </Space>
        ) : null,
    },
  ];

  // ===== Cột phân công =====
  const asgColumns: ColumnsType<ScheduleAssignment> = [
    { title: t('schedule.assignment.column.code'), dataIndex: 'employeeCode', width: 100 },
    { title: t('schedule.assignment.column.name'), dataIndex: 'fullName', width: 180, ellipsis: true },
    { title: t('schedule.assignment.column.policy'), dataIndex: 'policyName', width: 200, ellipsis: true },
    {
      title: t('schedule.assignment.column.effectiveFrom'),
      dataIndex: 'effectiveFrom',
      width: 120,
      render: (v: string) => dayjs(v).format('DD/MM/YYYY'),
    },
    {
      title: t('schedule.assignment.column.assignedBy'),
      dataIndex: 'assignedBy',
      width: 110,
      render: (v: string | null) => v ?? '—',
    },
    {
      title: t('schedule.assignment.column.current'),
      dataIndex: 'latest',
      width: 110,
      render: (v: boolean) => (v ? <Tag color="blue">{t('schedule.assignment.column.current')}</Tag> : null),
    },
    {
      title: t('actions'),
      width: 70,
      render: (_, a) =>
        canWrite && a.latest ? (
          <Popconfirm
            title={t('schedule.assignment.confirmDelete')}
            onConfirm={() => void removeAssign(a.id)}
          >
            <ActionIcon title={t('delete')} color="#ff4d4f">
              <DeleteOutlined />
            </ActionIcon>
          </Popconfirm>
        ) : null,
    },
  ];

  const cellEntry = cell?.entry;
  const shiftFormValue = Form.useWatch('shiftId', cellForm);

  return (
    <div>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {t('schedule.title')}
          </Typography.Title>
          <Text type="secondary">{t('schedule.subtitle')}</Text>
        </div>

        <Tabs
          activeKey={tab}
          onChange={setTab}
          items={[
            {
              key: 'calendar',
              label: (
                <span>
                  <CalendarOutlined /> {t('schedule.tab.calendar')}
                </span>
              ),
              children: (
                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                  <Space wrap>
                    <DatePicker.RangePicker
                      value={range}
                      allowClear={false}
                      onChange={(v) => {
                        if (!v || !v[0] || !v[1]) return;
                        const start = v[0];
                        let end = v[1];
                        if (end.diff(start, 'day') + 1 > MAX_RANGE_DAYS) {
                          end = start.add(MAX_RANGE_DAYS - 1, 'day');
                          message.warning(
                            t('schedule.calendar.rangeLimit', { days: MAX_RANGE_DAYS })
                          );
                        }
                        setRange([start, end]);
                      }}
                    />
                    <Select
                      allowClear
                      showSearch
                      optionFilterProp="label"
                      style={{ minWidth: 240 }}
                      placeholder={t('schedule.calendar.employee')}
                      value={empFilter}
                      onChange={setEmpFilter}
                      options={visibleEmployees.map((e) => ({
                        value: e.employeeId,
                        label: `${e.employeeCode} — ${e.fullName}`,
                      }))}
                    />
                    <Button icon={<ReloadOutlined />} onClick={() => void loadRosters()}>
                      {t('refresh')}
                    </Button>
                    <Tag color="orange">
                      ✎ {t('schedule.calendar.manualBadge')}
                    </Tag>
                  </Space>
                  <Table<AttendanceEmployee>
                    rowKey="employeeId"
                    size="small"
                    loading={calLoading}
                    columns={calColumns}
                    dataSource={visibleEmployees}
                    scroll={{ x: 170 + days.length * 78, y: '60vh' }}
                    pagination={{
                      current: page,
                      pageSize,
                      total: visibleEmployees.length,
                      hideOnSinglePage: true,
                      showSizeChanger: false,
                      onChange: (p, ps) => {
                        setPage(p);
                        setPageSize(ps);
                      },
                    }}
                    locale={{
                      emptyText: (
                        <Empty
                          description={t('schedule.calendar.empty')}
                          image={Empty.PRESENTED_IMAGE_SIMPLE}
                        />
                      ),
                    }}
                  />
                </Space>
              ),
            },
            {
              key: 'policies',
              label: (
                <span>
                  <CalendarOutlined /> {t('schedule.tab.policies')}
                </span>
              ),
              children: (
                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                  {canWrite && (
                    <Button type="primary" icon={<PlusOutlined />} onClick={openCreatePolicy}>
                      {t('schedule.policy.create')}
                    </Button>
                  )}
                  <Table<WorkSchedulePolicy>
                    rowKey="id"
                    size="small"
                    loading={polLoading}
                    columns={policyColumns}
                    dataSource={policies}
                    scroll={{ x: 1100 }}
                    pagination={{ pageSize: 20, hideOnSinglePage: true }}
                    locale={{
                      emptyText: (
                        <Empty
                          description={t('schedule.policy.empty')}
                          image={Empty.PRESENTED_IMAGE_SIMPLE}
                        />
                      ),
                    }}
                  />
                </Space>
              ),
            },
            {
              key: 'assignments',
              label: (
                <span>
                  <ToolOutlined /> {t('schedule.tab.assignments')}
                </span>
              ),
              children: (
                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                  {canWrite && (
                    <Button
                      type="primary"
                      icon={<PlusOutlined />}
                      onClick={() => {
                        asgForm.resetFields();
                        void searchEmps('');
                        setAsgOpen(true);
                      }}
                    >
                      {t('schedule.assignment.create')}
                    </Button>
                  )}
                  <Table<ScheduleAssignment>
                    rowKey="id"
                    size="small"
                    loading={asgLoading}
                    columns={asgColumns}
                    dataSource={assignments}
                    scroll={{ x: 900 }}
                    pagination={{ pageSize: 20, hideOnSinglePage: true }}
                    locale={{
                      emptyText: (
                        <Empty
                          description={t('schedule.assignment.empty')}
                          image={Empty.PRESENTED_IMAGE_SIMPLE}
                        />
                      ),
                    }}
                  />
                </Space>
              ),
            },
          ]}
        />
      </Space>

      {/* Modal 1 ngày */}
      <Modal
        title={
          cell
            ? t('schedule.cell.title', { date: cell.date.format('DD/MM/YYYY') })
            : ''
        }
        open={!!cell}
        onOk={() => void saveCell()}
        onCancel={() => setCell(null)}
        okText={t('schedule.cell.save')}
        cancelText={t('cancel')}
        okButtonProps={{ loading: cellSaving }}
        width={520}
        destroyOnClose
      >
        {cell && (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <div>
              <Text type="secondary">
                {cell.emp.employeeCode} — {cell.emp.fullName}
              </Text>
            </div>
            {cellEntry ? (
              <div>
                <Space size={8} wrap>
                  <Text type="secondary">{t('schedule.cell.source')}:</Text>
                  <Tag color={SOURCE_COLOR[cellEntry.source]}>
                    {t(`schedule.source.${cellEntry.source}`)}
                  </Tag>
                  {cellEntry.policyId && (
                    <>
                      <Text type="secondary">{t('schedule.cell.policy')}:</Text>
                      <Text code>{cellEntry.policyId.slice(0, 8)}</Text>
                    </>
                  )}
                </Space>
                {cellEntry.note && (
                  <div style={{ marginTop: 4 }}>
                    <Text type="secondary">{t('schedule.cell.note')}:</Text> {cellEntry.note}
                  </div>
                )}
              </div>
            ) : (
              <Text type="secondary">{t('schedule.cell.loading')}</Text>
            )}

            {editableCell && canWrite ? (
              <>
                <Form form={cellForm} layout="vertical">
                  <Form.Item name="shiftId" label={t('schedule.cell.shift')}>
                    <Select
                      options={[
                        { value: OFF, label: t('schedule.cell.dayOffOption') },
                        ...shiftSelectOptions(
                          shiftFormValue && shiftFormValue !== OFF
                            ? shiftFormValue
                            : cellEntry?.shiftId
                        ),
                      ]}
                    />
                  </Form.Item>
                  <Form.Item name="note" label={t('schedule.cell.note')}>
                    <Input.TextArea rows={2} maxLength={200} />
                  </Form.Item>
                </Form>
                {cellEntry?.source === 'MANUAL_OVERRIDE' && (
                  <div>
                    <Popconfirm
                      title={t('schedule.cell.clearConfirm', {
                        date: cell.date.format('DD/MM/YYYY'),
                      })}
                      onConfirm={() => void clearCell()}
                    >
                      <Button icon={<ReloadOutlined />} loading={cellSaving}>
                        {t('schedule.cell.clearOverride')}
                      </Button>
                    </Popconfirm>
                  </div>
                )}
              </>
            ) : (
              <Space>
                <Tag>{t('schedule.cell.viewOnly')}</Tag>
                <Text type="secondary">{t('schedule.cell.pastHint')}</Text>
              </Space>
            )}
          </Space>
        )}
      </Modal>

      {/* Modal chính sách */}
      <Modal
        title={
          editingPol ? t('schedule.policy.edit') : t('schedule.policy.create')
        }
        open={polModalOpen}
        onOk={() => void submitPolicy()}
        onCancel={() => setPolModalOpen(false)}
        okText={t('save')}
        cancelText={t('cancel')}
        width={620}
        destroyOnClose
      >
        <Form form={polForm} layout="vertical">
          <Form.Item
            name="name"
            label={t('schedule.policy.form.name')}
            rules={[{ required: true, message: t('required') }]}
          >
            <Input maxLength={100} placeholder={t('schedule.policy.form.namePlaceholder')} />
          </Form.Item>
          <Form.Item name="description" label={t('schedule.policy.form.description')}>
            <Input maxLength={500} />
          </Form.Item>

          <Text strong>{t('schedule.policy.form.week')}</Text>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(2, 1fr)',
              gap: '0 12px',
              marginTop: 8,
            }}
          >
            {POL_FIELDS.map((f) => (
              <Form.Item key={f} name={f} label={t(`schedule.policy.day.${f}`)} style={{ marginBottom: 12 }}>
                <Select
                  allowClear
                  placeholder={t('schedule.shiftOption.off')}
                  options={shiftSelectOptions((editingPol?.[`${f}ShiftId` as keyof WorkSchedulePolicy] as string | null) ?? null)}
                />
              </Form.Item>
            ))}
          </div>

          <Form.Item name="rotation" label={t('schedule.policy.form.rotation')} valuePropName="checked">
            <Switch />
          </Form.Item>
          {rotation && (
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(2, 1fr)',
                gap: '0 12px',
              }}
            >
              <Form.Item
                name="alternate"
                label={t('schedule.policy.form.alternate')}
                rules={[{ required: true, message: t('schedule.policy.form.alternateRequireAnchor') }]}
              >
                <Select
                  allowClear
                  placeholder={t('schedule.shiftOption.off')}
                  options={shiftSelectOptions(editingPol?.satAlternateShiftId ?? null)}
                />
              </Form.Item>
              <Form.Item
                name="anchor"
                label={t('schedule.policy.form.anchor')}
                rules={[
                  { required: true, message: t('schedule.policy.form.alternateRequireAnchor') },
                  {
                    validator: (_, v: Dayjs | null) =>
                      !v || v.day() === 6
                        ? Promise.resolve()
                        : Promise.reject(new Error(t('schedule.policy.form.anchorMustSaturday'))),
                  },
                ]}
              >
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </div>
          )}
          <div style={{ color: '#8c8c8c', fontSize: 12, marginTop: -8 }}>
            {t('schedule.policy.form.rotationHint')}
          </div>

          <Space size={24} style={{ marginTop: 16 }}>
            <Form.Item
              name="defaultPolicy"
              label={t('schedule.policy.form.default')}
              valuePropName="checked"
              style={{ marginBottom: 0 }}
            >
              <Switch />
            </Form.Item>
            <Form.Item
              name="active"
              label={t('schedule.policy.form.active')}
              valuePropName="checked"
              style={{ marginBottom: 0 }}
            >
              <Switch />
            </Form.Item>
          </Space>
          <Form.Item
            name="effectiveFrom"
            label={t('schedule.assignment.form.effectiveFrom')}
            rules={[{ required: true, message: t('required') }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Modal phân công */}
      <Modal
        title={t('schedule.assignment.create')}
        open={asgOpen}
        onOk={() => void submitAssign()}
        onCancel={() => setAsgOpen(false)}
        okText={t('save')}
        cancelText={t('cancel')}
        destroyOnClose
      >
        <Form form={asgForm} layout="vertical">
          <Form.Item
            name="employeeId"
            label={t('schedule.assignment.form.employee')}
            rules={[{ required: true, message: t('required') }]}
          >
            <Select
              showSearch
              filterOption={false}
              options={empOptions}
              onSearch={(kw) => void searchEmps(kw)}
              placeholder={t('schedule.assignment.form.employeePlaceholder')}
              notFoundContent={t('schedule.assignment.form.searchHint')}
            />
          </Form.Item>
          <Form.Item
            name="policyId"
            label={t('schedule.assignment.form.policy')}
            rules={[{ required: true, message: t('required') }]}
          >
            <Select
              options={policies
                .filter((p) => p.active)
                .map((p) => ({
                  value: p.id,
                  label: p.defaultPolicy ? `${p.name} ★` : p.name,
                }))}
              placeholder={t('schedule.assignment.form.policyPlaceholder')}
            />
          </Form.Item>
          <Form.Item
            name="effectiveFrom"
            label={t('schedule.assignment.form.effectiveFrom')}
            rules={[{ required: true, message: t('required') }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}