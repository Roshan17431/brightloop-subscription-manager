"use client";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { X } from "lucide-react";
import { Button } from "./button";
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  children,
  onConfirm,
  pending,
}: {
  open: boolean;
  onOpenChange: (value: boolean) => void;
  title: string;
  children: React.ReactNode;
  onConfirm: () => void;
  pending?: boolean;
}) {
  return (
    <DialogPrimitive.Root open={open} onOpenChange={onOpenChange}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-slate-950/35 backdrop-blur-sm" />
        <DialogPrimitive.Content className="fixed left-1/2 top-1/2 z-50 w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 rounded-2xl border border-slate-200 bg-white p-6 shadow-xl">
          <DialogPrimitive.Title className="text-lg font-semibold">
            {title}
          </DialogPrimitive.Title>
          <DialogPrimitive.Description asChild>
            <div className="my-4 text-sm leading-6 text-slate-500">
              {children}
            </div>
          </DialogPrimitive.Description>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={pending}
              onClick={onConfirm}
            >
              {pending ? "Deleting…" : "Delete record"}
            </Button>
          </div>
          <DialogPrimitive.Close
            className="absolute right-4 top-4 rounded p-1 text-slate-400"
            aria-label="Close dialog"
          >
            <X size={18} />
          </DialogPrimitive.Close>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
