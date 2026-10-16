import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import {
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
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { errorMessage } from '../api/client';
import {
  createCalendar,
  createHoliday,
  deleteCalendar,
  deleteHoliday,
  listCalendars,
  listHolidays,
  updateCalendar,
  updateHoliday,
} from '../api/holidays';
import type { Holiday, HolidayCalendar, HolidayTypeValue } from '../api/types';
import ActionIcon from '../components/ActionIcon';

const { Text } = Typography;

const TYPE_COLOR: Record<HolidayTypeValue, string> = {
  NATIONAL: 'red',
  COMPANY: 'blue',
  OTHER: 'default',
};

const WEEKDAYS = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];

function rangeDates(from: string, to: string): string[] {
  const out: string[] = [];
  let d = dayjs(from);
  const end = dayjs(to);
  while (!d.isAfter(end)) {
    out.push(d.format('YYYY-MM-DD'));
    d = d.add(1, 'day');
  }
  return out;
}

/** 12 tháng × lưới ngày — ngày lễ tô nền đỏ nhạt, hover hiện tên. */
function YearCalendar({
  year,
  holidays,
  onPickDate,
}: {
  year: number;
  holidays: Holiday[];
  onPickDate?: (date: string) => void;
}) {
  const { t } = useTranslation();
  const byDate = useMemo(() => {
    const map = new Map<string, Holiday[]>();
    for (const h of holidays) {
      for (const d of rangeDates(h.dateFrom, h.dateTo)) {
        const list = map.get(d) ?? [];
        list.push(h);
        map.set(d, list);
      }
    }
    return map;
  }, [holidays]);

  const today = dayjs().format('YYYY-MM-DD');

  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12 }}>
      {Array.from({ length: 12 }, (_, m) => {
        const first = dayjs(new Date(year, m, 1));
        const daysInMonth = first.daysInMonth();
        const lead = first.day(); // 0=CN
        const cells: (Dayjs | null)[] = [
          ...Array.from({ length: lead }, () => null),
          ...Array.from({ length: daysInMonth }, (_, d) => dayjs(new Date(year, m, d + 1))),
        ];
        while (cells.length % 7 !== 0) cells.push(null);
        return (
          <div
            key={m}
            style={{
              background: '#fff',
              border: '1px solid #e5e7eb',
              borderRadius: 8,
              padding: 8,
              boxShadow: '0 1px 2px rgba(0, 0, 0, 0.04)',
            }}
          >
            <Text strong style={{ display: 'block', textAlign: 'center', marginBottom: 4 }}>
              {first.format('MM/YYYY')}
            </Text>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(7, 1fr)',
                gap: 2,
                textAlign: 'center',
                fontSize: 11,
              }}
            >
              {WEEKDAYS.map((w) => (
                <Text key={w} type="secondary" style={{ fontSize: 10 }}>
                  {w}
                </Text>
              ))}
              {cells.map((day, i) => {
                if (!day) return <span key={i} />;
                const key = day.format('YYYY-MM-DD');
                const hits = byDate.get(key);
                const isToday = key === today;
                const cell = (
                  <div
                    key={i}
                    onClick={hits && onPickDate ? () => onPickDate(key) : undefined}
                    style={{
                      padding: '2px 0',
                      borderRadius: 4,
                      cursor: hits ? 'pointer' : 'default',
                      background: hits?.length ? '#fff1f0' : undefined,
                      border: isToday ? '1px solid #1677ff' : undefined,
                      fontWeight: hits?.length ? 600 : undefined,
                      color: hits?.length ? '#cf1322' : undefined,
                    }}
                  >
                    {day.date()}
                  </div>
                );
                return hits?.length ? (
                  <Tooltip
                    key={i}
                    title={hits.map((h) => `${h.name}${h.halfDay ? ` (${t('holiday.halfDay')})` : ''}`).join('\n')}
                  >
                    {cell}
                  </Tooltip>
                ) : (
                  cell
                );
              })}
            </div>
          </div>
        );
      })}
    </div>
  );
}

export default function HolidayPage() {
  const { t } = useTranslation();

  const currentYear = dayjs().year();
  const [year, setYear] = useState<number>(currentYear);
  const [calendars, setCalendars] = useState<HolidayCalendar[]>([]);
  const [calendarId, setCalendarId] = useState<string | null>(null);
  const [holidays, setHolidays] = useState<Holiday[]>([]);
  const [q, setQ] = useState('');
  const [loading, setLoading] = useState(false);

  const [holidayModalOpen, setHolidayModalOpen] = useState(false);
  const [editing, setEditing] = useState<Holiday | null>(null);
  const [holidayForm] = Form.useForm();

  const [calendarModalOpen, setCalendarModalOpen] = useState(false);
  const [editingCalendar, setEditingCalendar] = useState<HolidayCalendar | null>(null);
  const [calendarForm] = Form.useForm();

  const selectedCalendar = calendars.find((c) => c.id === calendarId) ?? null;

  const loadCalendars = useCallback(async () => {
    const data = await listCalendars();
    setCalendars(data);
    setCalendarId((prev) => prev ?? data.find((c) => c.code === 'VN')?.id ?? data[0]?.id ?? null);
    return data;
  }, []);

  const loadHolidays = useCallback(async () => {
    setLoading(true);
    try {
      const code = calendars.find((c) => c.id === calendarId)?.code;
      const data = await listHolidays({ calendarCode: code, year, q: q || undefined });
      setHolidays(data);
    } catch (e) {
      message.error(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }, [calendars, calendarId, year, q]);

  useEffect(() => {
    void loadCalendars();
  }, [loadCalendars]);

  useEffect(() => {
    if (calendarId) void loadHolidays();
  }, [calendarId, loadHolidays]);

  const openCreateHoliday = () => {
    setEditing(null);
    holidayForm.resetFields();
    holidayForm.setFieldsValue({ type: 'NATIONAL', halfDay: false });
    setHolidayModalOpen(true);
  };

  const openEditHoliday = (record: Holiday) => {
    setEditing(record);
    holidayForm.setFieldsValue({
      name: record.name,
      range: [dayjs(record.dateFrom), dayjs(record.dateTo)],
      type: record.type,
      halfDay: record.halfDay,
      note: record.note,
    });
    setHolidayModalOpen(true);
  };

  const submitHoliday = async () => {
    const values = await holidayForm.validateFields();
    const [from, to] = values.range as [Dayjs, Dayjs];
    const payload = {
      name: values.name.trim(),
      dateFrom: from.format('YYYY-MM-DD'),
      dateTo: to.format('YYYY-MM-DD'),
      type: values.type as HolidayTypeValue,
      halfDay: !!values.halfDay,
      note: values.note || undefined,
    };
    try {
      if (editing) {
        await updateHoliday(editing.id, payload);
      } else {
        if (!calendarId) return;
        await createHoliday(calendarId, payload);
      }
      message.success(t('saved'));
      setHolidayModalOpen(false);
      await loadHolidays();
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const submitRemoveHoliday = async (id: string) => {
    try {
      await deleteHoliday(id);
      message.success(t('deleted'));
      await loadHolidays();
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const openCreateCalendar = () => {
    setEditingCalendar(null);
    calendarForm.resetFields();
    calendarForm.setFieldsValue({ country: 'VN' });
    setCalendarModalOpen(true);
  };

  const openEditCalendar = () => {
    if (!selectedCalendar) return;
    setEditingCalendar(selectedCalendar);
    calendarForm.setFieldsValue({
      code: selectedCalendar.code,
      name: selectedCalendar.name,
      country: selectedCalendar.country,
      description: selectedCalendar.description,
      status: selectedCalendar.status === 'ACTIVE',
    });
    setCalendarModalOpen(true);
  };

  const submitCalendar = async () => {
    const values = await calendarForm.validateFields();
    try {
      if (editingCalendar) {
        await updateCalendar(editingCalendar.id, {
          name: values.name.trim(),
          description: values.description || undefined,
          status: values.status ? 'ACTIVE' : 'INACTIVE',
        });
      } else {
        const created = await createCalendar({
          code: values.code.trim().toUpperCase(),
          name: values.name.trim(),
          country: values.country.trim().toUpperCase(),
          description: values.description || undefined,
        });
        setCalendarId(created.id);
      }
      message.success(t('saved'));
      setCalendarModalOpen(false);
      await loadCalendars();
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const submitRemoveCalendar = async () => {
    if (!selectedCalendar) return;
    try {
      await deleteCalendar(selectedCalendar.id);
      message.success(t('deleted'));
      setCalendarId(null);
      await loadCalendars();
    } catch (e) {
      message.error(errorMessage(e));
    }
  };

  const columns: ColumnsType<Holiday> = [
    {
      title: t('holiday.holidayName'),
      dataIndex: 'name',
      render: (v: string, r) => (
        <Space>
          {v}
          {r.halfDay && <Tag color="orange">{t('holiday.halfDay')}</Tag>}
        </Space>
      ),
    },
    {
      title: t('holiday.dateFrom'),
      dataIndex: 'dateFrom',
      width: 110,
      render: (v: string) => dayjs(v).format('DD/MM/YYYY'),
    },
    {
      title: t('holiday.dateTo'),
      dataIndex: 'dateTo',
      width: 110,
      render: (v: string) => dayjs(v).format('DD/MM/YYYY'),
    },
    {
      title: t('holiday.type'),
      dataIndex: 'type',
      width: 100,
      render: (v: HolidayTypeValue) => <Tag color={TYPE_COLOR[v]}>{t(`holiday.types.${v}`)}</Tag>,
    },
    {
      title: t('holiday.note'),
      dataIndex: 'note',
      ellipsis: true,
      render: (v: string | null) => v ?? '—',
    },
    {
      title: t('holiday.actions'),
      width: 90,
      render: (_, r) => (
        <Space>
          <ActionIcon key="edit" title={t('edit')} color="#1677ff" onClick={() => openEditHoliday(r)}>
            <EditOutlined />
          </ActionIcon>
          <Popconfirm
            title={t('holiday.deleteConfirmTitle')}
            description={t('holiday.deleteConfirm')}
            onConfirm={() => submitRemoveHoliday(r.id)}
          >
            <ActionIcon title={t('delete')} color="#ff4d4f">
              <DeleteOutlined />
            </ActionIcon>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {t('holiday.title')}
          </Typography.Title>
          <Text type="secondary">{t('holiday.subtitle')}</Text>
        </div>

        <Space wrap>
          <Select
            style={{ minWidth: 240 }}
            placeholder={t('holiday.calendars')}
            value={calendarId}
            onChange={setCalendarId}
            options={calendars.map((c) => ({
              value: c.id,
              label: `${c.code} — ${c.name}`,
            }))}
          />
          <Select
            style={{ width: 110 }}
            value={year}
            onChange={setYear}
            options={Array.from({ length: 5 }, (_, i) => currentYear - 1 + i).map((y) => ({
              value: y,
              label: String(y),
            }))}
          />
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder={t('holiday.searchPlaceholder')}
            style={{ width: 220 }}
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateHoliday}>
            {t('holiday.newHoliday')}
          </Button>
          <Button icon={<PlusOutlined />} onClick={openCreateCalendar}>
            {t('holiday.newCalendar')}
          </Button>
          <Button icon={<EditOutlined />} disabled={!selectedCalendar} onClick={openEditCalendar}>
            {t('holiday.editCalendar')}
          </Button>
          <Popconfirm
            title={t('holiday.deleteCalendarConfirmTitle')}
            description={t('holiday.deleteCalendarConfirm')}
            onConfirm={submitRemoveCalendar}
          >
            <Button danger icon={<DeleteOutlined />} disabled={!selectedCalendar}>
              {t('holiday.deleteCalendarConfirmTitle')}
            </Button>
          </Popconfirm>
        </Space>

        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(480px, 1.1fr) 1fr', gap: 16 }}>
          <Table<Holiday>
            rowKey="id"
            size="small"
            loading={loading}
            columns={columns}
            dataSource={holidays}
            pagination={{ pageSize: 10, hideOnSinglePage: true }}
            locale={{
              emptyText: (
                <Empty description={calendarId ? t('holiday.empty') : t('holiday.selectCalendar')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ),
            }}
          />
          <div>
            {calendarId ? (
              <YearCalendar
                year={year}
                holidays={holidays}
                onPickDate={() => undefined}
              />
            ) : (
              <Empty description={t('holiday.selectCalendar')} />
            )}
          </div>
        </div>
      </Space>

      <Modal
        title={editing ? t('holiday.editHoliday') : t('holiday.newHoliday')}
        open={holidayModalOpen}
        onOk={submitHoliday}
        onCancel={() => setHolidayModalOpen(false)}
        destroyOnClose
        okText={t('save')}
        cancelText={t('cancel')}
      >
        <Form form={holidayForm} layout="vertical">
          <Form.Item
            name="name"
            label={t('holiday.holidayName')}
            rules={[{ required: true, message: t('required') }]}
          >
            <Input maxLength={200} />
          </Form.Item>
          <Form.Item
            name="range"
            label={`${t('holiday.dateFrom')} — ${t('holiday.dateTo')}`}
            rules={[{ required: true, message: t('required') }]}
          >
            <DatePicker.RangePicker style={{ width: '100%' }} format="DD/MM/YYYY" />
          </Form.Item>
          <Form.Item name="type" label={t('holiday.type')} rules={[{ required: true }]}>
            <Select
              options={(['NATIONAL', 'COMPANY', 'OTHER'] as HolidayTypeValue[]).map((v) => ({
                value: v,
                label: t(`holiday.types.${v}`),
              }))}
            />
          </Form.Item>
          <Form.Item name="halfDay" label={t('holiday.halfDay')} valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="note" label={t('holiday.note')}>
            <Input.TextArea rows={2} maxLength={300} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={editingCalendar ? t('holiday.editCalendar') : t('holiday.newCalendar')}
        open={calendarModalOpen}
        onOk={submitCalendar}
        onCancel={() => setCalendarModalOpen(false)}
        destroyOnClose
        okText={t('save')}
        cancelText={t('cancel')}
      >
        <Form form={calendarForm} layout="vertical">
          <Form.Item
            name="code"
            label={t('holiday.calendarCode')}
            rules={[{ required: !editingCalendar, message: t('required') }]}
          >
            <Input maxLength={30} disabled={!!editingCalendar} />
          </Form.Item>
          <Form.Item
            name="name"
            label={t('holiday.calendarName')}
            rules={[{ required: true, message: t('required') }]}
          >
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item
            name="country"
            label={t('holiday.country')}
            rules={[{ required: !editingCalendar, len: 2, message: 'ISO alpha-2' }]}
          >
            <Input maxLength={2} disabled={!!editingCalendar} />
          </Form.Item>
          <Form.Item name="description" label={t('holiday.description')}>
            <Input.TextArea rows={2} maxLength={300} />
          </Form.Item>
          {editingCalendar && (
            <Form.Item name="status" label={t('holiday.status')} valuePropName="checked" initialValue>
              <Switch checkedChildren={t('holiday.statusActive')} unCheckedChildren={t('holiday.statusInactive')} />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </div>
  );
}
