"use client";

import * as React from "react";
import {
  DayPicker,
  type ChevronProps, // v9 Chevron 타입
} from "react-day-picker";
import { ChevronLeft, ChevronRight, ChevronUp, ChevronDown } from "lucide-react";

import { cn } from "./utils";
import { buttonVariants } from "./button";

type CalendarProps = React.ComponentProps<typeof DayPicker>;

function Calendar({
  className,
  classNames,
  showOutsideDays = true,
  ...props
}: CalendarProps) {
  return (
    <DayPicker
      showOutsideDays={showOutsideDays}
      className={cn("p-3", className)}
      classNames={{
        // === 레이아웃(UI Enum 기반 스네이크케이스 키) ===
        root: "",
        months: "flex flex-col sm:flex-row gap-2",
        month: "flex flex-col gap-4",
        month_grid: "w-full border-collapse space-x-1",
        weeks: "",
        week: "flex w-full mt-2",

        // === 캡션/헤더 ===
        month_caption: "flex justify-center pt-1 relative items-center w-full",
        caption_label: "text-sm font-medium",

        // === 내비게이션 바 및 버튼 ===
        nav: "flex items-center gap-1",
        button_previous: cn(
          buttonVariants({ variant: "outline" }),
          "size-7 bg-transparent p-0 opacity-50 hover:opacity-100 absolute left-1",
        ),
        button_next: cn(
          buttonVariants({ variant: "outline" }),
          "size-7 bg-transparent p-0 opacity-50 hover:opacity-100 absolute right-1",
        ),

        // === 요일 헤더 ===
        weekdays: "flex",
        weekday: "text-muted-foreground rounded-md w-8 font-normal text-[0.8rem]",

        // === 날짜 셀/버튼 ===
        day: cn(
          "relative p-0 text-center text-sm focus-within:relative focus-within:z-20",
          props.mode === "range"
            ? "[&:has(>.day-range-end)]:rounded-r-md [&:has(>.day-range-start)]:rounded-l-md first:[&:has([aria-selected])]:rounded-l-md last:[&:has([aria-selected])]:rounded-r-md"
            : "[&:has([aria-selected])]:rounded-md",
          "[&:has([aria-selected])]:bg-accent",
        ),
        day_button: cn(
          buttonVariants({ variant: "ghost" }),
          "size-8 p-0 font-normal aria-selected:opacity-100",
        ),

        // === 상태/선택 관련 (SelectionState & DayFlag) ===
        selected:
          "bg-primary text-primary-foreground hover:bg-primary hover:text-primary-foreground focus:bg-primary focus:text-primary-foreground",
        today: "bg-accent text-accent-foreground",
        outside: "text-muted-foreground aria-selected:text-muted-foreground",
        disabled: "text-muted-foreground opacity-50",
        hidden: "invisible",
        range_start:
          "day-range-start aria-selected:bg-primary aria-selected:text-primary-foreground",
        range_end:
          "day-range-end aria-selected:bg-primary aria-selected:text-primary-foreground",
        range_middle:
          "aria-selected:bg-accent aria-selected:text-accent-foreground",

        // 사용자 전달 덮어쓰기 허용
        ...classNames,
      }}
      components={{
        // v9: IconLeft/Right 제거 → Chevron으로 통합
        Chevron: ({ orientation, className, ...rest }: ChevronProps) => {
          if (orientation === "left")
            return <ChevronLeft className={cn("size-4", className)} {...rest} />;
          if (orientation === "right")
            return <ChevronRight className={cn("size-4", className)} {...rest} />;
          if (orientation === "up")
            return <ChevronUp className={cn("size-4", className)} {...rest} />;
          return <ChevronDown className={cn("size-4", className)} {...rest} />;
        },
      }}
      {...props}
    />
  );
}

export { Calendar };
