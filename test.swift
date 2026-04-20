//
//  SimpleTodo.swift
//  Swift CLI ToDo App
//

import Foundation

struct Todo: Codable {
    let id: UUID
    var title: String
    var isDone: Bool
}

class TodoManager {
    private(set) var todos: [Todo] = []
    private let fileURL: URL

    init(filename: String = "todos.json") {
        let fm = FileManager.default
        let dir = fm.homeDirectoryForCurrentUser
        fileURL = dir.appendingPathComponent(filename)
        load()
    }

    func save() {
        do {
            let data = try JSONEncoder().encode(todos)
            try data.write(to: fileURL)
        } catch {
            print("Error saving todos:", error)
        }
    }

    func load() {
        do {
            let data = try Data(contentsOf: fileURL)
            todos = try JSONDecoder().decode([Todo].self, from: data)
        } catch {
            todos = []
        }
    }

    // 一覧表示
    func list() {
        if todos.isEmpty {
            print("== ToDo リストは空です ==")
        } else {
            print("== ToDo リスト ==")
            for (i, t) in todos.enumerated() {
                let mark = t.isDone ? "✅" : "🔲"
                print("\(i + 1): \(mark) \(t.title)")
            }
        }
    }

    // 追加
    func add(title: String) {
        let newTodo = Todo(id: UUID(), title: title, isDone: false)
        todos.append(newTodo)
        save()
        print("追加しました: \(title)")
    }

    // 完了
    func complete(index: Int) {
        guard todos.indices.contains(index) else {
            print("無効な番号です")
            return
        }
        todos[index].isDone = true
        save()
        print("完了: \(todos[index].title)")
    }

    // 削除
    func delete(index: Int) {
        guard todos.indices.contains(index) else {
            print("無効な番号です")
            return
        }
        let title = todos[index].title
        todos.remove(at: index)
        save()
        print("削除しました: \(title)")
    }
}

// ヘルパー: 標準入力読み込み
func readInput(prompt: String) -> String? {
    print(prompt, terminator: " ")
    return readLine()
}

// メイン
func main() {
    let manager = TodoManager()
    while true {
        print("""
        
        ==== ToDo メニュー ====
        1: 一覧表示
        2: 追加
        3: 完了にする
        4: 削除
        5: 終了
        =======================
        """)
        guard let choice = readInput(prompt: "番号を入力してください:"), let num = Int(choice) else {
            print("無効な入力")
            continue
        }

        switch num {
        case 1:
            manager.list()
        case 2:
            if let title = readInput(prompt: "タイトルを入力:") {
                manager.add(title: title)
            }
        case 3:
            manager.list()
            if let idxStr = readInput(prompt: "完了する番号:"), let idx = Int(idxStr) {
                manager.complete(index: idx - 1)
            }
        case 4:
            manager.list()
            if let idxStr = readInput(prompt: "削除する番号:"), let idx = Int(idxStr) {
                manager.delete(index: idx - 1)
            }
        case 5:
            print("終了します。")
            return
        default:
            print("1～5 の数字を入力してください")
        }
    }
}

// エントリポイント
main()
