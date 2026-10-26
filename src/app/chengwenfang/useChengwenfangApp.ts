import { useChengwenfangPart0 } from "./useChengwenfangPart0";
import { useChengwenfangPart1 } from "./useChengwenfangPart1";
import { useChengwenfangPart2 } from "./useChengwenfangPart2";
import { useChengwenfangPart3 } from "./useChengwenfangPart3";
import { useChengwenfangPart4 } from "./useChengwenfangPart4";
import { useChengwenfangPart5 } from "./useChengwenfangPart5";
import { useChengwenfangPart6 } from "./useChengwenfangPart6";
import { useChengwenfangPart7 } from "./useChengwenfangPart7";
import { useChengwenfangPart8 } from "./useChengwenfangPart8";

/** 组合各分段 hook，返回成文坊会话袋（供布局壳解构）。 */
export function useChengwenfangApp(): Record<string, any> {
  let session: Record<string, any> = useChengwenfangPart0();
  session = useChengwenfangPart1(session);
  session = useChengwenfangPart2(session);
  session = useChengwenfangPart3(session);
  session = useChengwenfangPart4(session);
  session = useChengwenfangPart5(session);
  session = useChengwenfangPart6(session);
  session = useChengwenfangPart7(session);
  session = useChengwenfangPart8(session);
  return session;
}
