import { Button, Tooltip } from 'antd';
import type { ButtonProps } from 'antd';
import type { ReactNode } from 'react';

/**
 * Nút icon trong cột thao tác của bảng — luôn kèm Tooltip.
 * Màu khuyến nghị: Sửa/Thay thế xanh dương, Gia hạn xanh lá,
 * Vô hiệu/Chấm dứt cam, Xóa đỏ, Hồ sơ/xem mặc định.
 */
export default function ActionIcon({
  title,
  color,
  children,
  ...btnProps
}: {
  title: string;
  color?: string;
  children: ReactNode;
} & Omit<ButtonProps, 'children' | 'title' | 'color' | 'style'>) {
  return (
    <Tooltip title={title}>
      <Button
        type="text"
        size="small"
        style={color ? { color, fontSize: 16 } : { fontSize: 16 }}
        {...btnProps}
      >
        {children}
      </Button>
    </Tooltip>
  );
}
