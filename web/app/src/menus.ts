// The app's hold menus (ui/MapOverlays: FirstPointMenu, PoiMenu, PointMenu): a small title, then one row per action
// (icon + label, an optional line under it); a danger row in red. Shown in a maplibre popup at the point.
import { icon, type IconName } from './icons';

export interface MenuItem { icon: IconName; label: string; sub?: string; danger?: boolean; disabled?: boolean;
  /** [row] = the clicked row (a [stay] row shows its new state there – setRow). */
  act: (row: HTMLButtonElement) => void;
  /** The menu stays open after the click (the app's kept-place rows, owner 2026-11-15: the menu shows the new state). */
  stay?: boolean;
  /** A thin line above the row (the app's PoiMenu: between the route rows and what is about the place itself). */
  divider?: boolean;
  /** The icon's own colour (the app's HoldMenuItem iconTint – the gold star of „★ Gemerkt“). */
  iconColor?: string;
  /** The app's row name (PinMenuItems: START_HERE, GO_HERE, ADD, …), for the rows' order. */
  key?: string;
  /** The likelier add row (the app's HoldMenuItem strong, owner 2026-11-15): tinted in the plan blue, a bold label. */
  strong?: boolean }

export function holdMenu(headHtml: string, items: MenuItem[], close: () => void): HTMLElement {
  const box = document.createElement('div');
  box.className = 'pointmenu';
  box.setAttribute('role', 'menu');
  box.innerHTML = `<div class="menuhead">${headHtml}</div>`;
  for (const it of items) {
    if (it.divider) { const hr = document.createElement('div'); hr.className = 'menudivider'; hr.setAttribute('role', 'separator'); box.append(hr); }
    const b = document.createElement('button');
    b.setAttribute('role', 'menuitem');
    b.className = it.danger ? 'danger' : it.strong ? 'strong' : '';
    b.disabled = !!it.disabled;
    setRow(b, it);
    b.onclick = (e) => { e.stopPropagation(); if (!it.stay) close(); it.act(b); };
    box.append(b);
  }
  return box;
}

/** A row's icon and texts – again after a [MenuItem.stay] row changed its state. */
export function setRow(b: HTMLButtonElement, it: Pick<MenuItem, 'icon' | 'label' | 'sub' | 'iconColor'>) {
  b.innerHTML = `${icon(it.icon)}<span class="txt"><span>${it.label}</span>${it.sub ? `<span class="muted small">${it.sub}</span>` : ''}</span>`;
  const ic = b.querySelector<SVGElement>('.ic');
  if (ic && it.iconColor) ic.style.fill = it.iconColor;
}
