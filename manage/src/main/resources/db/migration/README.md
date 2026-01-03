# Permission Type NULL Support

## Issue
The permission system needs to support `permissionType = null` to represent menus that are selected but have no explicit permission type (no access).

## Solution
Execute the following SQL migration to allow `permission_type` column to accept NULL values:

```sql
ALTER TABLE permissions MODIFY COLUMN permission_type VARCHAR(20) NULL COMMENT 'Permission type (null- 无权限, view- 查看权限, edit- 编辑权限, all- 全部权限)';
```

## Steps

1. Connect to your MySQL database
2. Select the appropriate database
3. Execute the SQL above
4. Verify the change:
   ```sql
   DESCRIBE permissions;
   ```
   The `permission_type` column should show `Null` as `YES`

## After Migration
Once this is done, the system will support:
- `permissionType: null` - Menu selected but no access permission
- `permissionType: 'view'` - View permission
- `permissionType: 'edit'` - Edit permission  
- `permissionType: 'all'` - All permissions

## Frontend Changes (Completed)
✅ Updated usePermissionManagement.ts to support null permission types
✅ Updated permissionService.ts to handle null values in requests
✅ Updated permissions.vue to show "None" option and handle null permissions
✅ Added "None" checkbox in edit modal
✅ Made permission checkboxes mutually exclusive using @click.exact

## Backend Changes (Completed)
✅ Updated PermissionService.java to accept null permission types
✅ Removed default "view" fallback to allow null values

## Testing
1. Refresh the permissions page
2. Open edit modal for a role
3. Select a menu and check "None" (this sets permissionType to null)
4. Save the permissions
5. Verify that the permission is saved with permission_type = NULL
6. View the permissions and confirm "None" badge is displayed
