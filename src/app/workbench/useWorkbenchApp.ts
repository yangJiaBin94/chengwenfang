import { useWorkbenchPart0 } from "./useWorkbenchPart0";
import { useWorkbenchPart1 } from "./useWorkbenchPart1";
import { useWorkbenchPart2 } from "./useWorkbenchPart2";
import { useWorkbenchPart3 } from "./useWorkbenchPart3";
import { useWorkbenchPart4 } from "./useWorkbenchPart4";
import { useWorkbenchPart5 } from "./useWorkbenchPart5";
import { useWorkbenchPart6 } from "./useWorkbenchPart6";
import { useWorkbenchPart7 } from "./useWorkbenchPart7";
import { useWorkbenchPart8 } from "./useWorkbenchPart8";

/** 组合各分段 hook，返回工作台会话袋（供布局壳解构）。 */
export function useWorkbenchApp(): Record<string, any> {
  let session: Record<string, any> = useWorkbenchPart0();
  session = useWorkbenchPart1(session);
  session = useWorkbenchPart2(session);
  session = useWorkbenchPart3(session);
  session = useWorkbenchPart4(session);
  session = useWorkbenchPart5(session);
  session = useWorkbenchPart6(session);
  session = useWorkbenchPart7(session);
  session = useWorkbenchPart8(session);
  return session;
}
